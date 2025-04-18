package az.ailab.lib.messaging.core.adapter;

import com.rabbitmq.client.Channel;
import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * Custom MessageListenerAdapter that handles @Payload annotation
 * and applies conversion for the first matching parameter.
 */
@Slf4j
public class PayloadAwareMessageListenerAdapter extends MessageListenerAdapter {

    public PayloadAwareMessageListenerAdapter(Object delegate) {
        super(delegate);
    }

    @Override
    protected Object[] buildListenerArguments(Object extractedMessage,
                                              Channel channel,
                                              Message message) {
        try {
            Method targetMethod = determineTargetMethod(getDefaultListenerMethod());
            if (targetMethod != null) {
                return Arrays.stream(targetMethod.getParameters())
                        .filter(param -> param.isAnnotationPresent(Payload.class))
                        .findFirst()
                        .map(param -> new Object[] {getMessageConverter().fromMessage(message)})
                        .orElseGet(() -> super.buildListenerArguments(extractedMessage, channel, message));
            }
        } catch (Exception e) {
            log.warn("Unable to build listener arguments based on @Payload: {}", e.getMessage());
        }

        return super.buildListenerArguments(extractedMessage, channel, message);
    }

    /**
     * Attempts to find the first method with the given name
     * and a parameter annotated with @Payload.
     */
    private Method determineTargetMethod(@NonNull String methodName) {
        Class<?> delegateClass = getDelegate().getClass();

        return Arrays.stream(delegateClass.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> Arrays.stream(method.getParameters())
                        .anyMatch(param -> param.isAnnotationPresent(Payload.class)))
                .findFirst()
                .orElse(null);
    }

}