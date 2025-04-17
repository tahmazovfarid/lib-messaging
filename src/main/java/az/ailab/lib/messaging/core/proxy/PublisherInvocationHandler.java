package az.ailab.lib.messaging.core.proxy;

import az.ailab.lib.messaging.annotation.CorrelationId;
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
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.messaging.handler.annotation.Payload;

/**
 * Handles the method invocations for the proxy-based publisher.
 */
@Slf4j
@RequiredArgsConstructor
public class PublisherInvocationHandler implements InvocationHandler {

    private final RoutingKeyResolver routingKeyResolver;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final ExchangeType exchangeType;
    private final String source;
    private final Map<Method, Routing> routingCache = new ConcurrentHashMap<>();

    public PublisherInvocationHandler(final RabbitTemplate rabbitTemplate,
                                      final RoutingKeyResolver routingKeyResolver,
                                      final String exchange,
                                      final ExchangeType exchangeType,
                                      final String source) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKeyResolver = routingKeyResolver;
        this.exchange = exchange;
        this.exchangeType = exchangeType;
        this.source = source;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        Routing routing = routingCache.computeIfAbsent(method,
                m -> AnnotationUtils.findAnnotation(m, Routing.class));

        if (routing == null) {
            log.error("Method '{}' in '{}' is missing @Routing annotation. Event will not be published.",
                    method.getName(), method.getDeclaringClass().getSimpleName());
            return null;
        }

        return publishMessage(method, args, routing);
    }

    private Object publishMessage(Method method, Object[] args, Routing routing) {
        if (args == null || args.length == 0) {
            log.error("Method '{}' requires at least one argument as payload.", method.getName());
            return null;
        }

        Object payload = extractPayload(method, args);
        String routingKey = routingKeyResolver.resolveRoutingKey(routing.key());
        Map<String, Object> headers = buildHeaders(method, routingKey, routing.confirm());
        String correlationId = extractCorrelationId(method, args);

        EventMessage<?> eventMessage = createEventMessage(payload, correlationId, headers);

        return sendToRabbitMQ(method, routingKey, routing.confirm(), eventMessage);
    }

    private Map<String, Object> buildHeaders(Method method, String routingKey, boolean confirm) {
        Map<String, Object> headers = new HashMap<>();

        headers.put("X-Exchange-Type", exchangeType.name());
        headers.put("X-Routing-Key", routingKey);
        headers.put("X-Publisher-Method", method.getDeclaringClass().getSimpleName() + "." + method.getName());

        if (confirm) {
            headers.put("X-Confirm-Delivery", true);
        }

        // TODO :: I will plan creating @Header annotation for insert header

        // Handle fanout exchange routing key warning
        if (exchangeType == ExchangeType.FANOUT && !routingKey.equals("#")) {
            log.debug("Routing key '{}' specified for FANOUT exchange '{}' will be ignored",
                    routingKey, exchange);
        }

        return headers;
    }

    private EventMessage<?> createEventMessage(Object payload, String correlationId, Map<String, Object> headers) {
        return EventMessage.builder()
                .payload(payload)
                .source(source)
                .correlationId(correlationId)
                .headers(headers)
                .build();
    }

    private Object sendToRabbitMQ(Method method, String routingKey, boolean confirm, EventMessage<?> eventMessage) {
        try {
            if (confirm) {
                rabbitTemplate.invoke(operations -> {
                    operations.convertAndSend(exchange, routingKey, eventMessage);
                    return operations.waitForConfirms(5000);
                });
            } else {
                rabbitTemplate.convertAndSend(exchange, routingKey, eventMessage);
            }

            log.debug("Published message with id: {} to exchange: {} with routing key: {} and {} headers",
                    eventMessage.id(), exchange, routingKey, eventMessage.headers().size());

            if (method.getReturnType() == String.class) {
                return eventMessage.id();
            } else if (method.getReturnType() == EventMessage.class) {
                return eventMessage;
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to publish message to exchange: {} with routing key: {}",
                    exchange, routingKey, e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    private Object extractPayload(Method method, Object[] args) {
        return IntStream.range(0, method.getParameterCount())
                .filter(i -> method.getParameters()[i].isAnnotationPresent(Payload.class))
                .mapToObj(i -> args[i])
                .findFirst()
                .orElse(args[0]);
    }

    private String extractCorrelationId(Method method, Object[] args) {
        if (args.length <= 1) {
            return null;
        }

        Parameter[] parameters = method.getParameters();

        return IntStream.range(1, Math.min(parameters.length, args.length))
                .filter(i -> parameters[i].isAnnotationPresent(CorrelationId.class))
                .mapToObj(i -> args[i])
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse(null);
    }

}