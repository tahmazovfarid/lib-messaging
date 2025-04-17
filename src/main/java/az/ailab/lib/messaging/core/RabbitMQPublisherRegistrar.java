package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.CorrelationId;
import az.ailab.lib.messaging.annotation.RabbitEventPublisher;
import az.ailab.lib.messaging.annotation.Routing;
import az.ailab.lib.messaging.core.proxy.PublisherInvocationHandler;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Dynamically scans and registers publisher interfaces annotated with {@link RabbitEventPublisher}.
 * <p>
 * For each annotated interface, a dynamic proxy is created that handles the publishing of events
 * to RabbitMQ using the provided metadata in annotations like {@link Routing} and {@link CorrelationId}.
 * </p>
 *
 * @author tahmazovfarid
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQPublisherRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final RoutingKeyResolver routingKeyResolver;
    private final RabbitTemplate rabbitTemplate;
    private final ExchangeNameResolver exchangeNameResolver;
    private final RabbitMQInfrastructure infrastructure;
    private final AmqpAdmin amqpAdmin;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    @Override
    public void onApplicationEvent(final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        log.debug("Scanning for publisher interfaces annotated with @RabbitEventPublisher");

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

        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) ctx).getBeanFactory();
        String beanName = generateBeanName(interfaceClass);

        if (beanFactory.containsSingleton(beanName)) {
            throw new IllegalStateException("A bean with name '" + beanName + "' is already registered.");
        }

        RabbitEventPublisher publisherAnnotation = interfaceClass.getAnnotation(RabbitEventPublisher.class);

        String exchange = publisherAnnotation.exchange();
        ExchangeType exchangeType = publisherAnnotation.exchangeType();
        String resolvedExchange = exchangeNameResolver.resolveExchangeName(exchange);
        String source = publisherAnnotation.source().isEmpty() ? applicationName : publisherAnnotation.source();
        boolean autoCreate = publisherAnnotation.autoCreate();

        if (autoCreate) {
            // Create exchange using the infrastructure config
            infrastructure.createExchange(amqpAdmin, resolvedExchange, exchangeType);
            log.debug("Exchange auto-created: {} [{}]", resolvedExchange, exchangeType);
        } else {
            log.info("Auto-creation disabled for exchange: {}, ensure it exists manually", resolvedExchange);
        }

        Object publisherInstance = createPublisherProxy(interfaceClass, resolvedExchange, exchangeType, source);

        // Register singleton bean directly
        beanFactory.registerSingleton(beanName, publisherInstance);

        log.info("Registered dynamic publisher bean: {} -> exchange: {}", beanName, resolvedExchange);
    }

    private String generateBeanName(Class<?> interfaceClass) {
        String simpleName = interfaceClass.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    @SuppressWarnings("unchecked")
    private <T> T createPublisherProxy(final Class<T> interfaceClass,
                                       final String exchange,
                                       final ExchangeType exchangeType,
                                       final String source) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] {interfaceClass},
                new PublisherInvocationHandler(rabbitTemplate, routingKeyResolver, exchange, exchangeType, source)
        );
    }

}