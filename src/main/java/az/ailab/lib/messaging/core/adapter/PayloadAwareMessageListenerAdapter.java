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
        Method method = findMethodByNameOnly(methodName);
        if (method != null) {
            Object[] convertedArgs = convertArguments(arguments, method);
            return method.invoke(getDelegate(), convertedArgs);
        }
        return super.invokeListenerMethod(methodName, arguments, originalMessage);
    }

    private Method findMethodByNameOnly(String methodName) {
        return Arrays.stream(getDelegate().getClass().getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElse(null);
    }

    private Object[] convertArguments(Object[] arguments, Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (arguments == null || arguments.length != paramTypes.length) {
            return arguments; // Return original if no conversion is needed
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

        try {
            if (arg instanceof byte[] bytes) {
                // Deserialize raw byte array directly
                return objectMapper.readValue(bytes, targetType);
            } else if (arg instanceof Message message) {
                // Extract body from Message object and deserialize
                return objectMapper.readValue(message.getBody(), targetType);
            } else {
                // Fallback: serialize to JSON and deserialize into target type
                return objectMapper.readValue(objectMapper.writeValueAsBytes(arg), targetType);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert argument for method: " + methodName, e);
        }
    }

}