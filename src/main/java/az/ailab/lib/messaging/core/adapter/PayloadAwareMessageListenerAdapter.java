package az.ailab.lib.messaging.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * A custom implementation of the {@link MessageListenerAdapter} class that dynamically resolves method
 * arguments based on the {@link Payload} annotation and invokes listener methods accordingly.
 *
 * <p>This adapter simplifies the handling of RabbitMQ messages by automatically converting the payload
 * to the required method parameter types using {@link ObjectMapper}. It supports both annotation-based
 * argument binding and default positional argument resolution.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *     <li>Automatically deserializes message payloads into method arguments using Spring's {@link Payload} annotation.</li>
 *     <li>Falls back to positional argument handling when no annotations are present.</li>
 *     <li>Provides detailed logging for debugging and troubleshooting invocation errors.</li>
 * </ul>
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

    /**
     * Creates a new instance of {@link PayloadAwareMessageListenerAdapter}.
     *
     * @param delegate         The target listener object that contains the methods to be invoked.
     * @param messageConverter The {@link MessageConverter} used for converting message payloads.
     */
    public PayloadAwareMessageListenerAdapter(final ObjectMapper objectMapper,
                                              final Object delegate,
                                              final MessageConverter messageConverter,
                                              final Method method) {
        super(delegate);
        this.objectMapper = objectMapper;
        this.method = method;
        this.setMessageConverter(messageConverter);
        this.setDefaultListenerMethod(method.getName());
    }

    /**
     * Dynamically invokes the listener method by resolving arguments based on their annotations and types.
     * This method handles payload deserialization for arguments annotated with {@link Payload}.
     *
     * @param methodName      The name of the listener method to be invoked.
     * @param arguments       The arguments provided for the method.
     * @param originalMessage The original RabbitMQ {@link Message} from which the payload is extracted.
     * @return The result of the invoked method, if any.
     */
    @SneakyThrows
    @Override
    protected Object invokeListenerMethod(String methodName, Object[] arguments, Message originalMessage) {
        log.debug("Invoking method: {} with arguments: {}", methodName, Arrays.toString(arguments));

        try {
            Object[] preparedArguments = prepareArguments(method, arguments, originalMessage);
            return method.invoke(getDelegate(), preparedArguments);
        } catch (Exception ex) {
            log.error("Failed to invoke method: {} - {}", methodName, ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Prepares the arguments for the target method by resolving payloads and positional arguments.
     *
     * @param method          The target listener method.
     * @param arguments       The raw arguments passed to the method.
     * @param originalMessage The original RabbitMQ {@link Message}.
     * @return An array of prepared arguments that match the method parameters.
     */
    private Object[] prepareArguments(Method method, Object[] arguments, Message originalMessage) {
        Parameter[] parameters = method.getParameters();
        Object[] preparedArgs = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];

            if (parameter.isAnnotationPresent(Payload.class)) {
                preparedArgs[i] = deserializePayload(originalMessage, parameter.getType());
            } else {
                preparedArgs[i] = resolveFallbackArgument(arguments, i);
            }
        }

        return preparedArgs;
    }

    /**
     * Deserializes the payload from the RabbitMQ {@link Message} into the specified target type.
     *
     * <p>This method uses {@link ObjectMapper} to convert the raw byte[] payload into the target type.
     * If deserialization fails, an exception is logged and thrown.</p>
     *
     * @param originalMessage The original message containing the payload.
     * @param targetType      The expected Java type for the payload.
     * @return An object of the specified target type, deserialized from the payload.
     * @throws RuntimeException If there is a deserialization error.
     */
    private Object deserializePayload(Message originalMessage, Class<?> targetType) {
        try {
            log.debug("{} to deserializing payload to type: {}", new String(originalMessage.getBody()), targetType.getName());
            return objectMapper.readValue(originalMessage.getBody(), targetType);
        } catch (Exception e) {
            log.error("Failed to deserialize payload to type: {}", targetType.getName(), e);
            throw new RuntimeException("Payload deserialization error", e);
        }
    }

    /**
     * Resolves arguments for method parameters when the {@link Payload} annotation is not present.
     *
     * <p>If the argument index is within bounds of the provided argument array, the argument is
     * returned as is. Otherwise, it defaults to {@code null}.</p>
     *
     * @param arguments The original list of provided arguments.
     * @param index     The index of the parameter for which to resolve the argument.
     * @return The resolved argument or {@code null} if the index is out of bounds.
     */
    private Object resolveFallbackArgument(Object[] arguments, int index) {
        if (arguments != null && arguments.length > index) {
            return arguments[index];
        }
        return null; // Defaults to null if no argument is available at the specified index
    }

}