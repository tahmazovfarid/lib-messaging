package az.ailab.lib.messaging.infra.adapter;

import az.ailab.lib.messaging.infra.annotation.Idempotent;
import az.ailab.lib.messaging.infra.annotation.Retryable;
import az.ailab.lib.messaging.infra.constants.RabbitHeaders;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import az.ailab.lib.messaging.infra.util.MessagePropertiesUtil;
import az.ailab.lib.messaging.infra.util.StackTraceUtil;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractContainerListenerAdapter implements ChannelAwareMessageListener {

    protected final EventProcessingContext eventProcessingContext;
    protected final IdempotencyService idempotencyService;

    protected abstract void handleMessage(Message message) throws Throwable;

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        String mId = Objects.requireNonNull(message.getMessageProperties().getMessageId(),
                "Missing messageId header");

        long tag = message.getMessageProperties().getDeliveryTag();

        if (skipIfDuplicate(mId)) {
            channel.basicAck(tag, false);
            return;
        }

        log.debug("Incoming message [messageId={}, exchange={}, routingKey={}]",
                mId, eventProcessingContext.exchange(), eventProcessingContext.routingKey());
        log.trace("Full payload: {}", new String(message.getBody(), StandardCharsets.UTF_8));

        try {
            handleMessage(message);
            markProcessed(mId);
            channel.basicAck(tag, false);
        } catch (Throwable ex) {
            markFailed(mId);
            handleException(ex, message, channel, tag);
        }
    }

    /**
     * Records a successful handling of the message by storing its identifier in the idempotency repository.
     * <p>
     * This method is only invoked for methods annotated with {@link Idempotent}. The message ID
     * will be stored with the TTL configured on the annotation, preventing reprocessing within the
     * valid time-window.
     *
     * @param mId the unique message identifier extracted from message headers
     */
    private void markProcessed(String mId) {
        if (eventProcessingContext.isIdempotencyEnabled()) {
            idempotencyService.markProcessed(mId);
        }
    }

    private void markFailed(String mId) {
        if (eventProcessingContext.isIdempotencyEnabled()) {
            idempotencyService.markFailed(mId);
        }
    }

    /**
     * Determines whether the incoming message should be skipped as a duplicate.
     * <p>
     * If the handler method carries the {@link Idempotent} annotation and the repository
     * already contains the message ID, this will return {@code true} to prevent
     * re-invocation of the handler.
     *
     * @param messageId the unique message identifier to check
     * @return {@code true} if the message has already been processed and should be skipped,
     * {@code false} to proceed with handling
     */
    private boolean skipIfDuplicate(String messageId) {
        if (eventProcessingContext.isIdempotencyEnabled()) {
            Idempotent idem = eventProcessingContext.idempotent();
            return !idempotencyService.shouldProcess(messageId, idem.ttlMs());
        }
        return false;
    }

    /**
     * Apply retry vs. dead-letter routing when the handler throws.
     *
     * @param ex      the thrown exception
     * @param message original AMQP message
     * @param channel RabbitMQ channel
     * @param tag     delivery tag for ack/nack
     * @throws IOException on publish or ack error
     */
    private void handleException(Throwable ex, Message message, Channel channel, long tag) throws Exception {
        String messageId = message.getMessageProperties().getMessageId();
        Map<String, Object> exceptionHeaders = prepareExceptionHeaders(ex, ex.getMessage());

        // 1) No retry configured → DLQ
        if (!eventProcessingContext.isRetryableEnabled()) {
            routeToDlxAndAck(message, channel, tag, "retry disabled or not configured", exceptionHeaders, messageId);
            return;
        }

        Class<? extends Throwable> exceptionClass = ex.getClass();

        // 2) Non-retryable exceptions?
        if (isNonRetryable(exceptionClass)) {
            routeToDlxAndAck(message, channel, tag,
                    "non-retryable exception: " + exceptionClass.getSimpleName(), exceptionHeaders, messageId);
            return;
        }

        // 3) Check attempt count
        long attempts = getXDeathCount(message);
        long maxAttempts = eventProcessingContext.retryable().maxAttempts();

        if (attempts >= maxAttempts) {
            routeToDlxAndAck(message, channel, tag, "exceeded maxAttempts.", exceptionHeaders, messageId);
        } else {
            routeToRetryAndNack(channel, tag, exceptionHeaders, messageId, attempts, maxAttempts);
        }
    }

    /**
     * Routes the message to retry logic by preparing exception headers and negatively acknowledging the message.
     * <p>
     * Adds exception details to message headers, then issues a negative acknowledgment (nack) without requeuing,
     * ensuring the message is routed to a retry or dead-letter exchange as configured.
     * Logs a warning including message metadata and failure cause.
     *
     * @param channel          the RabbitMQ channel used for acknowledgment
     * @param tag              the delivery tag of the message
     * @param exceptionHeaders the exception that occurred during processing
     * @param messageId        the unique identifier of the message
     * @param attempts         the current retry attempt count (zero-based)
     * @param maxAttempts      the maximum allowed retry attempts
     * @throws IOException if an I/O error occurs while sending the nack
     */
    private void routeToRetryAndNack(Channel channel,
                                     long tag,
                                     Map<String, Object> exceptionHeaders,
                                     String messageId,
                                     long attempts,
                                     long maxAttempts) throws IOException {
        channel.basicNack(tag, false, false);
        log.warn("Retrying [messageId={}, queue={}, routingKey={}] attempt {}/{} cause {}: {} \n\t {}",
                messageId, eventProcessingContext.queue(), eventProcessingContext.routingKey(),
                attempts + 1, maxAttempts,
                exceptionHeaders.get(RabbitHeaders.EXCEPTION_TYPE),
                exceptionHeaders.get(RabbitHeaders.EXCEPTION_MESSAGE),
                exceptionHeaders.get(RabbitHeaders.EXCEPTION_ORIGIN));
    }

    /**
     * Publish the message to DLX, ack it, and log the reason and exception.
     *
     * @param message          original AMQP message
     * @param channel          RabbitMQ channel
     * @param tag              delivery tag
     * @param reason           textual reason for DLQ routing
     * @param exceptionHeaders the triggering exception
     * @param messageId        the messageId header
     * @throws IOException on publish or ack failure
     */
    private void routeToDlxAndAck(Message message,
                                  Channel channel,
                                  long tag,
                                  String reason,
                                  Map<String, Object> exceptionHeaders,
                                  String messageId) throws IOException {
        basicPublishToDlx(message, channel, exceptionHeaders);
        channel.basicAck(tag, false);
        log.error("Published to DLX='{}', routingKey='{}', messageId={} cause:{}/'{}'",
                eventProcessingContext.dlxName(), eventProcessingContext.routingKey(), messageId,
                exceptionHeaders.get(RabbitHeaders.EXCEPTION_TYPE), reason);
    }

    /**
     * Republish the raw message to the dead-letter exchange (DLX).
     * Uses the same routing key and preserves headers and delivery mode.
     *
     * @param message original Spring AMQP message
     * @param channel RabbitMQ channel
     * @throws IOException on publish failure
     */
    private void basicPublishToDlx(Message message, Channel channel, Map<String, Object> exceptionHeaders) throws IOException {
        AMQP.BasicProperties props = MessagePropertiesUtil.toBasicProperties(message.getMessageProperties(), exceptionHeaders);
        channel.basicPublish(eventProcessingContext.dlxName(), eventProcessingContext.routingKey(), props, message.getBody());
    }

    /**
     * Count how many times this queue has seen the message,
     * based on the "x-death" header entries.
     *
     * @param message the AMQP message
     * @return death count for this queue (0 if none)
     */
    private long getXDeathCount(Message message) {
        List<Map<String, Object>> deaths =
                message.getMessageProperties().getHeader(RabbitHeaders.X_DEATH);
        if (deaths == null) {
            return 0;
        }
        return deaths.stream()
                .filter(d -> eventProcessingContext.queue().equals(d.get("queue")))
                .mapToLong(d -> (Long) d.get("count"))
                .findFirst()
                .orElse(0);
    }

    /**
     * Determine if the exception should never be retried
     * based on {@link Retryable#notRetryFor()} and {@link Retryable#retryFor()}.
     *
     * @param exceptionClass the thrown exception class
     * @return true if it is non-retryable
     */
    private boolean isNonRetryable(Class<? extends Throwable> exceptionClass) {
        Class<? extends Throwable>[] noRetry = eventProcessingContext.retryable().notRetryFor();
        Class<? extends Throwable>[] doRetry = eventProcessingContext.retryable().retryFor();

        // match any noRetryFor
        if (noRetry.length > 0 && isAssignableFromAny(noRetry, exceptionClass)) {
            return true;
        }
        // if retryFor list is provided and exception not in it → non-retryable
        return (doRetry.length > 0 && !isAssignableFromAny(doRetry, exceptionClass));
    }

    /**
     * Populates the message properties with exception details for auditing and error handling.
     *
     * @param ex     the exception that triggered header preparation
     * @param reason a textual description of the failure reason
     */
    private Map<String, Object> prepareExceptionHeaders(Throwable ex, String reason) {
        Map<String, Object> exceptionHeaders = new HashMap<>();

        exceptionHeaders.put(RabbitHeaders.EXCEPTION_TYPE, ex.getClass().getName());
        exceptionHeaders.put(RabbitHeaders.EXCEPTION_MESSAGE, ex.getMessage());
        exceptionHeaders.put(RabbitHeaders.FAILURE_REASON, reason);
        exceptionHeaders.put(RabbitHeaders.EXCEPTION_ORIGIN,
                StackTraceUtil.extractOriginException(ex.getStackTrace(),
                        eventProcessingContext.method().getDeclaringClass().getPackage().getName()));

        return exceptionHeaders;
    }

    /**
     * Helper to check if any of the provided classes is assignable
     * from the target exception class.
     *
     * @param classes     array of exception classes
     * @param targetClass actual thrown exception class
     * @return true if any matches
     */
    private boolean isAssignableFromAny(Class<? extends Throwable>[] classes,
                                        Class<?> targetClass) {
        return Arrays.stream(classes)
                .anyMatch(c -> c.isAssignableFrom(targetClass));
    }

}
