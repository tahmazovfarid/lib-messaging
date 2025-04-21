package az.ailab.lib.messaging.core.adapter;

import az.ailab.lib.messaging.core.EventMessage;
import az.ailab.lib.messaging.error.DeserializationException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * A custom implementation of {@link MessageListenerAdapter} that dynamically resolves
 * the first method argument as a deserialized payload and invokes the listener method.
 *
 * <p>This adapter simplifies handling of RabbitMQ messages by converting message payload
 * to the required method parameter types using {@link ObjectMapper}.</p>
 *
 * <p>Currently, only the first parameter is considered as payload, others are ignored.</p>
 *
 * @author tahmazovfarid
 */
@Slf4j
public class PayloadAwareMessageListenerAdapter extends MessageListenerAdapter {

    /**
     * The {@link ObjectMapper} instance used for payload deserialization.
     */
    private final ObjectMapper objectMapper;
    private final Method method;
    private final String queueName;

    /**
     * Creates a new instance of {@link PayloadAwareMessageListenerAdapter}.
     *
     * @param queueName        Name of the queue for logging context.
     * @param objectMapper     Jackson ObjectMapper used for deserialization.
     * @param delegate         Target listener object that contains the method.
     * @param messageConverter Spring AMQP message converter.
     * @param method           Target method to invoke on message arrival.
     */
    public PayloadAwareMessageListenerAdapter(final String queueName,
                                              final ObjectMapper objectMapper,
                                              final Object delegate,
                                              final MessageConverter messageConverter,
                                              final Method method) {
        super(delegate, messageConverter);
        super.setDefaultListenerMethod(method.getName());
        this.objectMapper = objectMapper;
        this.method = method;
        this.queueName = queueName;
    }

    /**
     * Resolves and prepares arguments for the listener method, then invokes it.
     *
     * @param methodName      The name of the method to invoke.
     * @param arguments       Ignored; arguments are prepared from message.
     * @param originalMessage The original RabbitMQ message.
     * @return The result of the invoked method.
     * @throws ListenerExecutionFailedException If invocation fails.
     */
    @Override
    protected Object invokeListenerMethod(String methodName, Object[] arguments, Message originalMessage) {
        log.trace("Invoking listener method '{}' on delegate class '{}'",
                method.getName(), getDelegate().getClass().getSimpleName());

        try {
            Object[] args = prepareArguments(method, originalMessage);
            return method.invoke(getDelegate(), args);
        } catch (IllegalAccessException e) {
            log.error("Illegal access to method: {}", method.getName(), e);
            throw new ListenerExecutionFailedException("Access error while invoking listener method", e);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            log.error("Exception while invoking listener method: {}", method.getName(), targetException);
            throw new ListenerExecutionFailedException("Exception during listener method invocation", targetException);
        } catch (Exception e) {
            log.error("Unexpected error during listener invocation", e);
            throw new ListenerExecutionFailedException("Unexpected error", e);
        }

    }

    /**
     * Prepares arguments for the listener method by deserializing only the first parameter.
     * All other parameters will be passed as {@code null}.
     *
     * @param method          Target listener method.
     * @param originalMessage The original RabbitMQ message.
     * @return An array of resolved method arguments.
     */
    private Object[] prepareArguments(Method method, Message originalMessage) {
        Parameter[] parameters = method.getParameters();
        Object[] resolvedArgs = new Object[parameters.length];

        if (parameters.length > 0) {
            Class<?> payloadType = parameters[0].getType();
            resolvedArgs[0] = deserializePayload(originalMessage, payloadType);
        }

        return resolvedArgs;
    }

    /**
     * Deserializes the message payload into the expected target type.
     *
     * @param originalMessage The original RabbitMQ message.
     * @param targetType      The expected Java class type for the payload.
     * @return Deserialized payload object.
     * @throws DeserializationException If payload cannot be deserialized.
     */
    private Object deserializePayload(Message originalMessage, Class<?> targetType) {
        String rawMessage = new String(originalMessage.getBody(), StandardCharsets.UTF_8);

        try {
            return isRawEventMessage(targetType) ?
                    deserializeRawEventMessage(originalMessage) : deserializeTypedPayload(originalMessage, targetType);
        } catch (Exception e) {
            log.error("Failed to deserialize message payload to type '{}'. Raw message: {}", targetType.getName(), rawMessage);
            throw new DeserializationException("Payload deserialization error", e);
        }
    }

    /**
     * Deserializes the raw {@link Message} body into an {@link EventMessage} without specifying payload type.
     *
     * @param message The RabbitMQ message to deserialize.
     * @return The deserialized {@link EventMessage} with raw payload.
     * @throws IOException If the message body cannot be parsed.
     */
    private EventMessage<?> deserializeRawEventMessage(Message message) throws IOException {
        EventMessage<?> eventMessage = objectMapper.readValue(message.getBody(), EventMessage.class);
        log.info("Received event message from [{}]: {}", queueName, eventMessage.id());

        return eventMessage;
    }

    /**
     * Deserializes the {@link Message} body into an {@link EventMessage} with a typed payload.
     *
     * @param message    The RabbitMQ message to deserialize.
     * @param targetType The target class of the payload type.
     * @return The deserialized payload object from the {@link EventMessage}.
     * @throws IOException If deserialization fails.
     */
    private Object deserializeTypedPayload(Message message, Class<?> targetType) throws IOException {
        JavaType javaType = objectMapper.getTypeFactory()
                .constructParametricType(EventMessage.class, targetType);
        EventMessage<?> eventMessage = objectMapper.readValue(message.getBody(), javaType);
        log.info("Received event message from [{}]: {}", queueName, eventMessage.id());

        return eventMessage.payload();
    }

    /**
     * Checks whether the given class is assignable from {@link EventMessage}.
     *
     * @param targetType The class to check.
     * @return {@code true} if the target type is assignable from {@link EventMessage}, otherwise {@code false}.
     */
    private boolean isRawEventMessage(Class<?> targetType) {
        return EventMessage.class.isAssignableFrom(targetType);
    }

}