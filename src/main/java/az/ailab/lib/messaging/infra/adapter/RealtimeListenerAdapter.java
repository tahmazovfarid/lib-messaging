package az.ailab.lib.messaging.infra.adapter;

import az.ailab.lib.messaging.infra.dto.EventMessage;
import az.ailab.lib.messaging.infra.annotation.Retryable;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import az.ailab.lib.messaging.infra.error.DeserializationException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * {@link ChannelAwareMessageListener} that invokes a single
 * {@code @RabbitEventHandler} method on each incoming message,
 * using MANUAL ack/nack and native Rabbit retry + DLQ routing
 * based on a {@link Retryable} configuration.
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
public class RealtimeListenerAdapter extends AbstractContainerListenerAdapter {

    private final ObjectMapper objectMapper;

    public RealtimeListenerAdapter(EventProcessingContext eventProcessingContext, IdempotencyService idempotencyService,
                                   ObjectMapper objectMapper) {
        super(eventProcessingContext, idempotencyService);
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleMessage(Message message) throws Throwable {
        try {
            Object[] preparedArguments = prepareArguments(eventProcessingContext.method(), message);
            eventProcessingContext.method().invoke(eventProcessingContext.bean(), preparedArguments);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
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

}