package az.ailab.lib.messaging.core.listener.adapter;

import az.ailab.lib.messaging.constants.RabbitHeaders;
import az.ailab.lib.messaging.core.EventMessage;
import az.ailab.lib.messaging.core.listener.annotation.Idempotent;
import az.ailab.lib.messaging.core.listener.annotation.Retry;
import az.ailab.lib.messaging.core.listener.idempotency.IdempotencyService;
import az.ailab.lib.messaging.error.DeserializationException;
import az.ailab.lib.messaging.util.MessagePropertiesUtil;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * {@link ChannelAwareMessageListener} that invokes a single
 * {@code @RabbitEventHandler} method on each incoming message,
 * using MANUAL ack/nack and native Rabbit retry + DLQ routing
 * based on a {@link Retry} configuration.
 * <p>
 * Incoming bodies are deserialized via Jackson into either {@link EventMessage}
 * or raw payload types, then passed as the first argument to the handler method.
 * </p>
 * <p>
 * If the handler throws:
 * <ul>
 *   <li>and retry is disabled or exception is non-retryable → republish to DLQ.</li>
 *   <li>and retryable but maxAttempts reached → republish to DLQ.</li>
 *   <li>and retryable with attempts left → nack to trigger native TTL-based retry.</li>
 * </ul>
 * </p>
 *
 * @author tahmazovfarid
 * @since 1.2
 */
@Slf4j
public class ContainerListenerAdapter implements ChannelAwareMessageListener {

    private final Object bean;
    private final Method method;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final String queueName;
    private final String routingKey;
    private final Retry retryConfig;
    private final String dlxName;
    private final String exchangeName;

    /**
     * Create a new adapter for a {@code @RabbitEventHandler} method.
     *
     * @param bean         target bean instance
     * @param method       method to invoke on message arrival
     * @param objectMapper Jackson mapper for payload deserialization
     * @param exchangeName exchange for this handler
     * @param dlxName      dlx for this handler
     * @param routingKey   routing key for this handler
     * @param queueName    queue this listener is bound to
     * @param retryConfig  retry settings (maybe null or disabled)
     */
    public ContainerListenerAdapter(@NonNull final Object bean,
                                    @NonNull final Method method,
                                    @NonNull final ObjectMapper objectMapper,
                                    final IdempotencyService idempotencyService,
                                    @NonNull final String exchangeName,
                                    @NonNull final String dlxName,
                                    @NonNull final String routingKey,
                                    @NonNull final String queueName,
                                    Retry retryConfig) {
        this.bean = bean;
        this.method = method;
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.exchangeName = exchangeName;
        this.dlxName = dlxName;
        this.routingKey = routingKey;
        this.queueName = queueName;
        this.retryConfig = retryConfig;
    }

    /**
     * Handle an incoming AMQP message: deserialize, invoke handler,
     * and ack/nack according to retry/DLQ logic.
     *
     * @param message incoming Spring AMQP Message
     * @param channel RabbitMQ Channel for ack/nack/publish
     * @throws Exception if handler invocation or DLQ publish fails
     */
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        String mId = Objects.requireNonNull(message.getMessageProperties().getMessageId(),
                "Missing messageId header");

        long tag = message.getMessageProperties().getDeliveryTag();

        if (skipIfDuplicate(mId)) {
            channel.basicAck(tag, false);
            return;
        }

        log.debug("Incoming message [messageId={}, exchange={}, routingKey={}]", mId, exchangeName, routingKey);
        log.trace("Full payload: {}", new String(message.getBody(), StandardCharsets.UTF_8));

        try {
            Object[] preparedArguments = prepareArguments(method, message);
            method.invoke(bean, preparedArguments);
            markProcessed(mId);
            channel.basicAck(tag, false);
        } catch (InvocationTargetException ex) {
            markFailed(mId);
            Throwable target = ex.getTargetException();
            handleException(target, message, channel, tag);
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
        if (method.isAnnotationPresent(Idempotent.class)) {
            idempotencyService.markProcessed(mId);
        }
    }

    private void markFailed(String mId) {
        if (method.isAnnotationPresent(Idempotent.class)) {
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
     *         {@code false} to proceed with handling
     */
    private boolean skipIfDuplicate(String messageId) {
        if (method.isAnnotationPresent(Idempotent.class)) {
            Idempotent idem = method.getAnnotation(Idempotent.class);
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

        // 1) No retry configured → DLQ
        if (retryConfig == null || !retryConfig.enabled()) {
            routeToDlxAndAck(message, channel, tag, "retry disabled or not configured", ex, messageId);
            return;
        }

        Class<? extends Throwable> exceptionClass = ex.getClass();

        // 2) Non-retryable exceptions?
        if (isNonRetryable(exceptionClass)) {
            routeToDlxAndAck(message, channel, tag,
                    "non-retryable exception: " + exceptionClass.getSimpleName(), ex, messageId);
            return;
        }

        // 3) Check attempt count
        long attempts = getXDeathCount(message);
        long maxAttempts = retryConfig.maxAttempts();

        if (attempts >= maxAttempts) {
            routeToDlxAndAck(message, channel, tag, "exceeded maxAttempts.", ex, messageId);
        } else {
            routeToRetryAndNack(message, channel, tag, ex, messageId, attempts, maxAttempts);
        }
    }

    /**
     * Routes the message to retry logic by preparing exception headers and negatively acknowledging the message.
     * <p>
     * Adds exception details to message headers, then issues a negative acknowledgment (nack) without requeuing,
     * ensuring the message is routed to a retry or dead-letter exchange as configured.
     * Logs a warning including message metadata and failure cause.
     *
     * @param message     the incoming AMQP message
     * @param channel     the RabbitMQ channel used for acknowledgment
     * @param tag         the delivery tag of the message
     * @param ex          the exception that occurred during processing
     * @param messageId   the unique identifier of the message
     * @param attempts    the current retry attempt count (zero-based)
     * @param maxAttempts the maximum allowed retry attempts
     * @throws IOException if an I/O error occurs while sending the nack
     */
    private void routeToRetryAndNack(Message message,
                                     Channel channel,
                                     long tag,
                                     Throwable ex,
                                     String messageId,
                                     long attempts,
                                     long maxAttempts) throws IOException {
        prepareExceptionHeaders(ex, message, ex.getMessage());
        channel.basicNack(tag, false, false);
        log.warn("Retrying [messageId={}, queue={}, routingKey={}] attempt {}/{} cause {}: {}",
                messageId, queueName, routingKey,
                attempts + 1, maxAttempts,
                ex.getClass().getSimpleName(), ex.getMessage());
    }

    /**
     * Publish the message to DLX, ack it, and log the reason and exception.
     *
     * @param message   original AMQP message
     * @param channel   RabbitMQ channel
     * @param tag       delivery tag
     * @param reason    textual reason for DLQ routing
     * @param ex        the triggering exception
     * @param messageId the messageId header
     * @throws IOException on publish or ack failure
     */
    private void routeToDlxAndAck(Message message,
                                  Channel channel,
                                  long tag,
                                  String reason,
                                  Throwable ex,
                                  String messageId) throws IOException {
        prepareExceptionHeaders(ex, message, reason);
        basicPublishToDlx(message, channel);
        channel.basicAck(tag, false);
        log.error("Published to DLX='{}', routingKey='{}', messageId={} cause:{}/'{}'",
                dlxName, routingKey, messageId, ex.getClass().getSimpleName(), reason);
    }

    /**
     * Republish the raw message to the dead-letter exchange (DLX).
     * Uses the same routing key and preserves headers and delivery mode.
     *
     * @param message original Spring AMQP message
     * @param channel RabbitMQ channel
     * @throws IOException on publish failure
     */
    private void basicPublishToDlx(Message message, Channel channel) throws IOException {
        byte[] body = message.getBody();
        AMQP.BasicProperties props = MessagePropertiesUtil.toBasicProperties(message.getMessageProperties());
        channel.basicPublish(dlxName, routingKey, props, body);
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
                .filter(d -> queueName.equals(d.get("queue")))
                .mapToLong(d -> (Long) d.get("count"))
                .findFirst()
                .orElse(0);
    }

    /**
     * Determine if the exception should never be retried
     * based on {@link Retry#notRetryFor()} and {@link Retry#retryFor()}.
     *
     * @param exceptionClass the thrown exception class
     * @return true if it is non-retryable
     */
    private boolean isNonRetryable(Class<? extends Throwable> exceptionClass) {
        Class<? extends Throwable>[] noRetry = retryConfig.notRetryFor();
        Class<? extends Throwable>[] doRetry = retryConfig.retryFor();

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
     * @param ex      the exception that triggered header preparation
     * @param message the AMQP message whose headers will be updated
     * @param reason  a textual description of the failure reason
     */
    private void prepareExceptionHeaders(Throwable ex, Message message, String reason) {
        message.getMessageProperties().setHeader(RabbitHeaders.EXCEPTION_TYPE, ex.getClass().getName());
        message.getMessageProperties().setHeader(RabbitHeaders.EXCEPTION_MESSAGE, ex.getMessage());
        message.getMessageProperties().setHeader(RabbitHeaders.FAILURE_REASON, reason);
        message.getMessageProperties().setHeader(RabbitHeaders.EXCEPTION_ORIGIN, extractOriginException(ex.getStackTrace()));
    }

    /**
     * Determines the origin of the exception by scanning the stack trace for the first element
     * whose class name starts with the first two segments of the listener method's package.
     * <p>
     * If no matching element is found, returns the first element of the stack trace or a placeholder.
     *
     * @param stack the stack trace elements from the thrown exception
     * @return a string representation of the most relevant stack trace element
     */
    private String extractOriginException(StackTraceElement[] stack) {
        String pkg = method.getDeclaringClass().getPackage().getName();
        String[] parts = pkg.split("\\.");
        String prefix = parts.length >= 2
                ? parts[0] + "." + parts[1]
                : pkg;

        for (StackTraceElement el : stack) {
            if (el.getClassName().startsWith(prefix)) {
                return el.toString();
            }
        }
        return stack.length > 0 ? stack[0].toString() : "<no-stack-trace>";
    }

    /**
     * Prepare method arguments by deserializing only the first parameter
     * into either {@link EventMessage} or its payload type.
     * Remaining parameters will be null.
     *
     * @param method  target handler method
     * @param message original AMQP message
     * @return array of arguments to invoke the method with
     */
    private Object[] prepareArguments(Method method, Message message) {
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];

        if (params.length > 0) {
            args[0] = deserializePayload(message, params[0].getType());
        }

        return args;
    }

    /**
     * Deserialize the message body into the desired target type.
     * If the target is {@link EventMessage}, returns the full wrapper;
     * otherwise extracts and returns just the payload.
     *
     * @param message    original AMQP message
     * @param targetType expected Java type
     * @return deserialized object
     * @throws DeserializationException on parse errors
     */
    private Object deserializePayload(Message message, Class<?> targetType) {
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (EventMessage.class.isAssignableFrom(targetType)) {
                return objectMapper.readValue(message.getBody(), EventMessage.class);
            }
            JavaType javaType = objectMapper.getTypeFactory()
                    .constructParametricType(EventMessage.class, targetType);
            EventMessage<?> ev = objectMapper.readValue(message.getBody(), javaType);
            return ev.getPayload();
        } catch (IOException e) {
            log.error("Failed to deserialize to {}. Raw message: {}", targetType.getName(), raw);
            throw new DeserializationException("Payload deserialization error", e);
        }
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