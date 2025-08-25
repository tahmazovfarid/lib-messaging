package az.ailab.lib.messaging.infra.proxy;

import az.ailab.lib.messaging.core.annotation.CorrelationId;
import az.ailab.lib.messaging.infra.annotation.EventHeader;
import az.ailab.lib.messaging.infra.annotation.EventHeaders;
import az.ailab.lib.messaging.infra.dto.EventMessage;
import az.ailab.lib.messaging.infra.annotation.Routing;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import az.ailab.lib.messaging.infra.resolver.RoutingKeyResolver;
import az.ailab.lib.messaging.infra.error.PublishFailureException;
import az.ailab.lib.messaging.core.enums.ExchangeType;
import az.ailab.lib.messaging.infra.util.MessagePropertiesUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * {@link InvocationHandler} implementation for dynamic RabbitMQ event publishers.
 * <p>
 * Intercepts interface methods annotated with {@link Routing}, wraps arguments into an
 * {@link EventMessage}, serializes to JSON, and publishes via {@link RabbitTemplate}
 * using manual {@link Message} construction to include {@code messageId} and custom headers.
 * </p>
 * <p>
 * Supports:
 * <ul>
 *   <li>Extracting payload from @Payload-annotated parameter or first argument</li>
 *   <li>Extracting correlationId from @CorrelationId-annotated parameter</li>
 *   <li>Merging custom headers from @EventHeaders (Map) and @EventHeader parameters</li>
 *   <li>Returning either the {@code EventMessage.id()} or the entire {@link EventMessage}</li>
 * </ul>
 * </p>
 *
 * @author tahmazovfarid
 * @since 1.0
 */
@Slf4j
public class PublisherInvocationHandler implements InvocationHandler {

    private final RabbitTemplate rabbitTemplate;
    private final RoutingKeyResolver routingKeyResolver;
    private final String exchange;
    private final ExchangeType exchangeType;
    private final String source;
    private final EventSerializer eventSerializer;

    private final Map<Method, Routing> routingCache = new ConcurrentHashMap<>();
    private final Map<Method, Integer> payloadIndexCache = new ConcurrentHashMap<>();
    private final Map<Method, Integer> correlationIndexCache = new ConcurrentHashMap<>();
    private final Map<Method, Map<String, Object>> defaultHeaderCache = new ConcurrentHashMap<>();
    private final Map<Method, List<Integer>> eventHeadersIdxCache = new ConcurrentHashMap<>();
    private final Map<Method, List<Integer>> eventHeaderIdxCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new {@link PublisherInvocationHandler}.
     *
     * @param rabbitTemplate     the RabbitTemplate for sending messages
     * @param routingKeyResolver resolves routing key strings
     * @param exchange           the RabbitMQ exchange name
     * @param exchangeType       the type of the exchange (DIRECT, TOPIC, etc.)
     * @param source             application/source identifier for EventMessage
     */
    public PublisherInvocationHandler(@NonNull final RabbitTemplate rabbitTemplate,
                                      @NonNull final RoutingKeyResolver routingKeyResolver,
                                      @NonNull final String exchange,
                                      @NonNull final ExchangeType exchangeType,
                                      @NonNull final String source,
                                      @NonNull final EventSerializer eventSerializer) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKeyResolver = routingKeyResolver;
        this.exchange = exchange;
        this.exchangeType = exchangeType;
        this.source = source;
        this.eventSerializer = eventSerializer;
    }

    /**
     * Intercepts all proxy method calls.
     * <p>
     * If the method is annotated with {@link Routing}, publishes an EventMessage;
     * otherwise delegates to {@link Object} methods.
     * </p>
     *
     * @param proxy  the proxy instance
     * @param method the invoked method
     * @param args   the method arguments
     * @return messageId (String), EventMessage, or null
     * @throws Throwable if serialization or sending fails
     */
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        Routing routing = routingCache.computeIfAbsent(method, m -> AnnotationUtils.findAnnotation(m, Routing.class));
        if (routing == null) {
            log.error("Missing @Routing on {}.{} — skipping publish", method.getDeclaringClass().getSimpleName(), method.getName());
            return null;
        }

        try {
            return publishMessage(method, args, routing);
        } catch (Exception e) {
            log.error("Error publishing for {}.{}", method.getDeclaringClass().getSimpleName(), method.getName(), e);
            throw e;
        }
    }

    /**
     * Builds and sends the {@link EventMessage}.
     *
     * @param method  invoked interface method
     * @param args    method arguments
     * @param routing routing metadata annotation
     * @return the messageId (String) or EventMessage based on return type
     */
    private Object publishMessage(Method method, Object[] args, Routing routing) {
        if (args == null || args.length == 0) {
            log.error("{} requires at least one argument", method.getName());
            return null;
        }

        String routingKey = routingKeyResolver.resolveRoutingKey(routing.key());
        Object payload = extractPayload(method, args);

        EventMessage<?> eventMessage;
        if (payload instanceof EventMessage) {
            eventMessage = (EventMessage<?>) payload;
        } else {
            String correlationId = extractCorrelationId(method, args);
            Map<String, Object> headers = new HashMap<>(getDefaultHeaders(method, routingKey, routing.confirm()));
            mergeCustomHeaders(method, args, headers);

            eventMessage = new EventMessage<>(payload, source, correlationId, headers);
        }

        return sendToRabbitMQ(method, routingKey, routing, eventMessage);
    }

    /**
     * Sends the EventMessage to RabbitMQ and handles confirm logic.
     *
     * @param method       invoked method
     * @param routingKey   routing key to use
     * @param routing      metadata annotation
     * @param eventMessage message to send
     * @return return value based on method signature
     */
    @SneakyThrows
    private Object sendToRabbitMQ(@NonNull final Method method,
                                  @NonNull final String routingKey,
                                  final Routing routing,
                                  @NonNull final EventMessage<?> eventMessage) {
        byte[] body = eventSerializer.serialize(eventMessage);

        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(eventMessage.getId());
        props.setHeaders(eventMessage.getHeaders());

        Message message = new Message(body, props);
        try {
            boolean confirm = routing.confirm();
            if (confirm) {
                boolean nack = Boolean.FALSE.equals(rabbitTemplate.execute(channel -> {
                    channel.basicPublish(exchange, routingKey, MessagePropertiesUtil.toBasicProperties(props), body);

                    return channel.waitForConfirms(routing.timeout());
                }));
                if (nack) {
                    throw new PublishFailureException(exchange, routingKey, eventMessage);
                }
            } else {
                rabbitTemplate.send(exchange, routingKey, message);
            }
            log.info("Published event [id={}] to exchange '{}' with key '{}', confirm is {}, headers={}",
                    eventMessage.getId(), exchange, routingKey, confirm, eventMessage.getHeaders().size());

            Class<?> returnType = method.getReturnType();
            if (String.class.equals(returnType)) {
                return eventMessage.getId();
            } else if (EventMessage.class.equals(returnType)) {
                return eventMessage;
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to publish to {} with key {}", exchange, routingKey);
            throw new PublishFailureException(exchange, routingKey, eventMessage);
        }
    }

    /**
     * Returns or caches default headers for a method.
     *
     * @param method     the publisher method
     * @param routingKey resolved routing key
     * @param confirm    whether you confirm header should be set
     * @return unmodifiable map of default headers
     */
    private Map<String, Object> getDefaultHeaders(Method method, String routingKey, boolean confirm) {
        return defaultHeaderCache.computeIfAbsent(method, m -> {
            Map<String, Object> headers = new HashMap<>(4);
            headers.put("X-Exchange-Type", exchangeType.name());
            headers.put("X-Routing-Key", routingKey);
            headers.put("X-Publisher-Method", m.getDeclaringClass().getSimpleName() + "." + m.getName());
            if (confirm) {
                headers.put("X-Confirm-Delivery", true);
            }
            return headers;
        });
    }

    /**
     * Merges custom headers from parameters annotated with @EventHeaders or @EventHeader.
     *
     * @param method  the publisher method
     * @param args    the method arguments
     * @param headers the headers map to augment
     */
    private void mergeCustomHeaders(Method method, Object[] args, Map<String, Object> headers) {
        List<Integer> mapIdxs = eventHeadersIdxCache.computeIfAbsent(method, m ->
                IntStream.range(0, m.getParameterCount())
                        .filter(i -> m.getParameters()[i].isAnnotationPresent(EventHeaders.class))
                        .boxed().toList()
        );
        List<Integer> singleIdxs = eventHeaderIdxCache.computeIfAbsent(method, m ->
                IntStream.range(0, m.getParameterCount())
                        .filter(i -> m.getParameters()[i].isAnnotationPresent(EventHeader.class))
                        .boxed().toList()
        );
        // @EventHeaders
        for (int idx : mapIdxs) {
            Object arg = args[idx];
            if (arg instanceof Map<?, ?> mp) {
                mp.forEach((k, v) -> headers.put(k.toString(), v));
            } else {
                log.warn("@EventHeaders param[{}] not Map: {}", idx, arg);
            }
        }
        // @EventHeader
        for (int idx : singleIdxs) {
            EventHeader ann = method.getParameters()[idx].getAnnotation(EventHeader.class);
            headers.put(ann.name(), args[idx]);
        }
    }

    /**
     * Finds the index of the first @Payload parameter or defaults to 0.
     *
     * @param method the publisher method
     * @param args   the method arguments
     * @return the payload object
     */
    private Object extractPayload(Method method, Object[] args) {
        int idx = payloadIndexCache.computeIfAbsent(method, m -> {
            Parameter[] ps = m.getParameters();
            for (int i = 0; i < ps.length; i++) {
                if (ps[i].isAnnotationPresent(Payload.class)) {
                    return i;
                }
            }
            return 0;
        });
        return args.length > idx ? args[idx] : args[0];
    }

    /**
     * Finds the index of the first @CorrelationId parameter or returns -1.
     *
     * @param method the publisher method
     * @param args   the method arguments
     * @return the correlationId string or null if none
     */
    private String extractCorrelationId(Method method, Object[] args) {
        int idx = correlationIndexCache.computeIfAbsent(method, m -> {
            Parameter[] ps = m.getParameters();
            for (int i = 0; i < ps.length; i++) {
                if (ps[i].isAnnotationPresent(CorrelationId.class)) {
                    return i;
                }
            }
            return -1;
        });
        return (idx >= 0 && idx < args.length && args[idx] != null)
                ? args[idx].toString()
                : null;
    }

}
