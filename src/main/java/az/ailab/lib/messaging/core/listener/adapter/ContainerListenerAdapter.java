package az.ailab.lib.messaging.core.listener.adapter;

import az.ailab.lib.messaging.constants.RabbitHeaders;
import az.ailab.lib.messaging.core.EventMessage;
import az.ailab.lib.messaging.core.listener.annotation.Retry;
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
 * @since 1.1
 */
@Slf4j
public class ContainerListenerAdapter implements ChannelAwareMessageListener {

    private final Object bean;
    private final Method method;
    private final ObjectMapper objectMapper;
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
                                    @NonNull final String exchangeName,
                                    @NonNull final String dlxName,
                                    @NonNull final String routingKey,
                                    @NonNull final String queueName,
                                    Retry retryConfig) {
        this.bean = bean;
        this.method = method;
        this.objectMapper = objectMapper;
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
        String mId = message.getMessageProperties().getMessageId();
        log.debug("Incoming message [messageId={}, exchange={}, routingKey={}]", mId, exchangeName, routingKey);

        log.trace("Full payload: {}", new String(message.getBody(), StandardCharsets.UTF_8));

        long tag = message.getMessageProperties().getDeliveryTag();

        try {
            Object[] preparedArguments = prepareArguments(method, message);
            method.invoke(bean, preparedArguments);
            channel.basicAck(tag, false);
        } catch (InvocationTargetException ex) {
            Throwable target = ex.getTargetException();
            handleException(target, message, channel, tag);
        } catch (Throwable ex) {
            handleException(ex, message, channel, tag);
        }
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
            routeToDlxAndAck(message, channel, tag,
                    "exceeded maxAttempts.", ex, messageId);
        } else {
            channel.basicNack(tag, false, false);
            log.warn("Retrying [messageId={}, queue={}, routingKey={}] attempt {}/{} cause {}: {}",
                    messageId, queueName, routingKey,
                    attempts + 1, maxAttempts,
                    exceptionClass.getSimpleName(), ex.getMessage());
        }
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
        message.getMessageProperties().setHeader("exceptionType", ex.getClass().getName());
        message.getMessageProperties().setHeader("exceptionMessage", ex.getMessage());
        message.getMessageProperties().setHeader("failureReason", reason);
        basicPublishToDlx(message, channel);
        channel.basicAck(tag, false);
        log.error("Published to DLX='{}', routingKey='{}', messageId={} because '{}'", dlxName, routingKey, messageId, reason);
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