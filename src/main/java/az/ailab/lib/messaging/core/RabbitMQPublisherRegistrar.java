package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.CorrelationId;
import az.ailab.lib.messaging.annotation.RabbitEventPublisher;
import az.ailab.lib.messaging.annotation.Routing;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * Registrar that processes {@link RabbitEventPublisher} annotations and creates dynamic implementations
 * for interfaces that publish messages to RabbitMQ.
 *
 * <p>This class scans for interfaces annotated with {@link RabbitEventPublisher} and creates dynamic
 * proxy implementations that handle message publishing to RabbitMQ.</p>
 *
 * @author tahmazovfarid
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQPublisherRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ExchangeNameResolver exchangeNameResolver;
    private final RoutingKeyResolver routingKeyResolver;
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        log.debug("Scanning for publisher interfaces");

        Arrays.stream(ctx.getBeanDefinitionNames())
                .map(beanName -> ((ConfigurableApplicationContext) ctx).getBeanFactory().getBeanDefinition(beanName))
                .map(BeanDefinition::getBeanClassName)
                .filter(Objects::nonNull)
                .forEach(beanClassName -> processBean(beanClassName, ctx));
    }

    private void processBean(final String beanClassName, final ApplicationContext ctx) {
        try {
            Class<?> beanClass = Class.forName(beanClassName);
            if (beanClass.isInterface() && beanClass.isAnnotationPresent(RabbitEventPublisher.class)) {
                registerPublisherBean(beanClass, ctx);
            }
        } catch (ClassNotFoundException e) {
            log.warn("Failed to load class: {}", beanClassName, e);
        }
    }

    private void registerPublisherBean(final Class<?> interfaceClass, final ApplicationContext ctx) {
        log.debug("Registering publisher for interface: {}", interfaceClass.getName());

        String beanName = generateBeanName(interfaceClass);
        RabbitEventPublisher publisherAnnotation = interfaceClass.getAnnotation(RabbitEventPublisher.class);

        String exchange = publisherAnnotation.exchange();
        ExchangeType exchangeType = publisherAnnotation.exchangeType();
        String resolvedExchange = exchangeNameResolver.resolveExchangeName(exchange);
        String source = publisherAnnotation.source().isEmpty() ? applicationName : publisherAnnotation.source();

        log.debug("Publisher configured for exchange: {} of type: {}", resolvedExchange, exchangeType);

        Object publisherInstance = createPublisherProxy(
                interfaceClass,
                resolvedExchange,
                exchangeType,
                source
        );

        // Register singleton bean directly
        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) ctx).getBeanFactory();
        beanFactory.registerSingleton(beanName, publisherInstance);

        log.info("Registered publisher bean: {} for exchange: {}", beanName, resolvedExchange);
    }

    private String generateBeanName(Class<?> interfaceClass) {
        return Character.toLowerCase(interfaceClass.getSimpleName().charAt(0)) +
                interfaceClass.getSimpleName().substring(1);
    }

    @SuppressWarnings("unchecked")
    private <T> T createPublisherProxy(final Class<T> interfaceClass,
                                       final String exchange,
                                       final ExchangeType exchangeType,
                                       final String source) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] {interfaceClass},
                new PublisherInvocationHandler(exchange, exchangeType, source)
        );
    }

    private class PublisherInvocationHandler implements InvocationHandler {
        private final String exchange;
        private final ExchangeType exchangeType;
        private final String source;
        private final Map<Method, Routing> routingCache = new ConcurrentHashMap<>();

        public PublisherInvocationHandler(final String exchange, final ExchangeType exchangeType, final String source) {
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
                log.warn("Method {} does not have @Routing annotation", method.getName());
                return null;
            }

            return publishMessage(method, args, routing);
        }

        private Object publishMessage(Method method, Object[] args, Routing routing) {
            if (args == null || args.length == 0) {
                log.warn("No payload provided for method: {}", method.getName());
                return null;
            }

            String routingKey = routingKeyResolver.resolveRoutingKey(routing.key());
            Map<String, Object> headers = buildHeaders(method, routingKey, routing.confirm());

            String correlationId = extractCorrelationId(method, args);
            EventMessage<?> eventMessage = createEventMessage(args[0], correlationId, headers);

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

}