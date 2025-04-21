package az.ailab.lib.messaging.core.proxy;

import az.ailab.lib.messaging.annotation.CorrelationId;
import az.ailab.lib.messaging.annotation.EventHeader;
import az.ailab.lib.messaging.annotation.EventHeaders;
import az.ailab.lib.messaging.annotation.Routing;
import az.ailab.lib.messaging.core.EventMessage;
import az.ailab.lib.messaging.core.ExchangeType;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * Handles method invocations for dynamic Rabbit event publishers.
 * <p>
 * Intercepts interface methods annotated with {@link Routing}, builds an
 * {@link EventMessage}, merges headers, and publishes to the configured exchange.
 * </p>
 */
@Slf4j
public class PublisherInvocationHandler implements InvocationHandler {

    private static final String HEADER_EXCHANGE_TYPE = "X-Exchange-Type";
    private static final String HEADER_ROUTING_KEY = "X-Routing-Key";
    private static final String HEADER_PUBLISHER_METHOD = "X-Publisher-Method";
    private static final String HEADER_CONFIRM_DELIVERY = "X-Confirm-Delivery";

    private final RabbitTemplate rabbitTemplate;
    private final RoutingKeyResolver routingKeyResolver;
    private final String exchange;
    private final ExchangeType exchangeType;
    private final String source;

    private final Map<Method, Routing> routingCache = new ConcurrentHashMap<>();
    private final Map<Method, Integer> payloadIndexCache = new ConcurrentHashMap<>();
    private final Map<Method, Integer> correlationIndexCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new PublisherInvocationHandler.
     *
     * @param rabbitTemplate     the RabbitTemplate used to send messages
     * @param routingKeyResolver resolver for routing key values
     * @param exchange           name of the target exchange
     * @param exchangeType       type of the RabbitMQ exchange
     * @param source             identifier used as message source metadata
     */
    public PublisherInvocationHandler(RabbitTemplate rabbitTemplate,
                                      RoutingKeyResolver routingKeyResolver,
                                      String exchange,
                                      ExchangeType exchangeType,
                                      String source) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate must not be null");
        this.routingKeyResolver = Objects.requireNonNull(routingKeyResolver, "routingKeyResolver must not be null");
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.exchangeType = Objects.requireNonNull(exchangeType, "exchangeType must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
    }

    /**
     * Intercepts calls on proxy instances. Handles methods declared on Object
     * and methods annotated with {@link Routing} to publish messages.
     *
     * @param proxy  the proxy instance
     * @param method the invoked method
     * @param args   method arguments
     * @return result based on method return type (e.g., message ID or EventMessage)
     * @throws Throwable if underlying method invocation fails
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        Routing routing = routingCache.computeIfAbsent(method,
                m -> AnnotationUtils.findAnnotation(m, Routing.class));
        if (routing == null) {
            log.error("@Routing missing on {}.{} - skipping publish", method.getDeclaringClass().getSimpleName(), method.getName());
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
     * Builds and sends the EventMessage for the given method.
     *
     * @param method  the invoked interface method
     * @param args    method arguments
     * @param routing routing metadata annotation
     * @return return value according to method signature
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
            Map<String, Object> headers = buildHeaders(method, routingKey, routing.confirm());
            mergeCustomHeaders(method, args, headers);
            eventMessage = createEventMessage(payload, correlationId, headers);
        }

        return sendToRabbitMQ(method, routingKey, routing.confirm(), eventMessage);
    }

    /**
     * Constructs the base headers for every message.
     *
     * @param method     invoked method
     * @param routingKey resolved routing key
     * @param confirm    flag for delivery confirmation
     * @return map of default headers
     */
    private Map<String, Object> buildHeaders(Method method, String routingKey, boolean confirm) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_EXCHANGE_TYPE, exchangeType.name());
        headers.put(HEADER_ROUTING_KEY, routingKey);
        headers.put(HEADER_PUBLISHER_METHOD, method.getDeclaringClass().getSimpleName() + "." + method.getName());
        if (confirm) {
            headers.put(HEADER_CONFIRM_DELIVERY, true);
        }
        if (exchangeType == ExchangeType.FANOUT && !"#".equals(routingKey)) {
            log.debug("FANOUT exchange ignores routing key {}", routingKey);
        }
        return headers;
    }

    /**
     * Merges custom headers from method parameters annotated with @Headers or @Header.
     *
     * @param method  invoked method
     * @param args    method arguments
     * @param headers existing header map to augment
     */
    private void mergeCustomHeaders(Method method, Object[] args, Map<String, Object> headers) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(EventHeaders.class)) {
                Object arg = args[i];
                if (arg instanceof Map<?, ?>) {
                    ((Map<?, ?>) arg).forEach((k, v) -> headers.put(k.toString(), v));
                } else {
                    log.warn("@Headers parameter at index {} is not a Map", i);
                }
            }
            if (params[i].isAnnotationPresent(EventHeader.class)) {
                EventHeader annotation = params[i].getAnnotation(EventHeader.class);
                headers.put(annotation.name(), args[i]);
            }
        }
    }

    /**
     * Wraps the payload, source, correlationId, and headers into an EventMessage.
     *
     * @param payload       message payload
     * @param correlationId optional correlation identifier
     * @param headers       map of headers
     * @return built EventMessage
     */
    private EventMessage<?> createEventMessage(Object payload,
                                               String correlationId,
                                               Map<String, Object> headers) {
        return EventMessage.builder()
                .payload(payload)
                .source(source)
                .correlationId(correlationId)
                .headers(headers)
                .build();
    }

    /**
     * Sends the EventMessage to RabbitMQ and handles confirm logic.
     *
     * @param method       invoked method
     * @param routingKey   routing key to use
     * @param confirm      whether to wait for confirms
     * @param eventMessage message to send
     * @return return value based on method signature
     */
    private Object sendToRabbitMQ(Method method,
                                  String routingKey,
                                  boolean confirm,
                                  EventMessage<?> eventMessage) {
        try {
            if (confirm) {
                boolean ok = Boolean.TRUE.equals(rabbitTemplate.invoke(ch -> {
                    ch.convertAndSend(exchange, routingKey, eventMessage);
                    return ch.waitForConfirms(5000);
                }));
                log.debug("Confirm enabled, delivery status: {}", ok);
            } else {
                rabbitTemplate.convertAndSend(exchange, routingKey, eventMessage);
            }
            log.info("Published [id={}] to exchange '{}' with key '{}' (headers={})",
                    eventMessage.id(), exchange, routingKey, eventMessage.headers().size());

            Class<?> returnType = method.getReturnType();
            if (String.class.equals(returnType)) {
                return eventMessage.id();
            } else if (EventMessage.class.equals(returnType)) {
                return eventMessage;
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to publish to {} with key {}", exchange, routingKey, e);
            throw new RuntimeException("Publish failure", e);
        }
    }

    /**
     * Finds the argument to use as payload (first @Payload or fallback to arg[0]).
     *
     * @param method invoked method
     * @param args   method arguments
     * @return the payload object to wrap in EventMessage
     */
    private Object extractPayload(Method method, Object[] args) {
        int idx = payloadIndexCache.computeIfAbsent(method, m -> {
            Parameter[] parameters = m.getParameters();
            for (int j = 0; j < parameters.length; j++) {
                if (parameters[j].isAnnotationPresent(Payload.class)) {
                    return j;
                }
            }
            return 0;
        });
        return args.length > idx ? args[idx] : args[0];
    }

    /**
     * Finds the first @CorrelationId parameter and returns its string value.
     *
     * @param method invoked method
     * @param args   method arguments
     * @return correlationId or null if none
     */
    private String extractCorrelationId(Method method, Object[] args) {
        int idx = correlationIndexCache.computeIfAbsent(method, m -> {
            Parameter[] parameters = m.getParameters();
            for (int j = 0; j < parameters.length; j++) {
                if (parameters[j].isAnnotationPresent(CorrelationId.class)) {
                    return j;
                }
            }
            return -1;
        });
        if (idx >= 0 && idx < args.length && args[idx] != null) {
            return args[idx].toString();
        }
        return null;
    }

}
