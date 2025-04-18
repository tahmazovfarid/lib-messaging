package az.ailab.lib.messaging.core.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Custom MessageListenerAdapter that dynamically converts payloads
 * and invokes listener methods by name only.
 */
@Slf4j
public class PayloadAwareMessageListenerAdapter extends MessageListenerAdapter {

    private final ObjectMapper objectMapper;

    public PayloadAwareMessageListenerAdapter(final Object delegate,
                                              final MessageConverter messageConverter,
                                              final Method method) {
        super(delegate);
        this.objectMapper = new ObjectMapper();
        this.setMessageConverter(messageConverter);
        this.setDefaultListenerMethod(method.getName());
    }

    @SneakyThrows
    @Override
    protected Object invokeListenerMethod(String methodName, Object[] arguments, Message originalMessage) {
        log.info("Attempting to invoke method: {}", methodName);
        Method method = findMethodByNameOnly(methodName, Arrays.stream(arguments)
                .map(arg -> arg != null ? arg.getClass() : null)
                .toArray(Class<?>[]::new));
        if (method == null) {
            log.error("Method: {} was not found!", methodName);
            throw new IllegalArgumentException("Invalid method: " + methodName);
        }
        Object[] convertedArgs = convertArguments(arguments, method);
        return method.invoke(getDelegate(), convertedArgs);
    }

    private Method findMethodByNameOnly(String methodName, Class<?>[] argumentTypes) {
        return Arrays.stream(getDelegate().getClass().getMethods())
                .filter(method -> method.getName().equals(methodName)
                        && Arrays.equals(method.getParameterTypes(), argumentTypes))
                .findFirst()
                .orElse(null);
    }

    private Object[] convertArguments(Object[] arguments, Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (arguments == null || arguments.length != paramTypes.length) {
            return arguments;
        }

        Object[] converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            converted[i] = convertArgument(arguments[i], paramTypes[i], method.getName());
        }
        return converted;
    }

    private Object convertArgument(Object arg, Class<?> targetType, String methodName) {
        if (arg == null || targetType.isAssignableFrom(arg.getClass())) {
            return arg; // No conversion needed
        }
        return deserialize(arg, targetType);
    }

    private <T> T deserialize(Object source, Class<T> targetType) {
        try {
            if (source instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, targetType);
            } else if (source instanceof Message message) {
                return objectMapper.readValue(message.getBody(), targetType);
            } else {
                return objectMapper.readValue(objectMapper.writeValueAsBytes(source), targetType);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize: " + targetType, e);
        }
    }

}