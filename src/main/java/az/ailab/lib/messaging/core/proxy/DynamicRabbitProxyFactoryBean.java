package az.ailab.lib.messaging.core.proxy;

import az.ailab.lib.messaging.annotation.RabbitEventPublisher;
import az.ailab.lib.messaging.core.ExchangeType;
import az.ailab.lib.messaging.core.RabbitInfrastructure;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.lang.reflect.Proxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.FactoryBean;

@Slf4j
public class DynamicRabbitProxyFactoryBean<T> implements FactoryBean<T> {

    private final RoutingKeyResolver routingKeyResolver;
    private final ExchangeNameResolver exchangeNameResolver;
    private final RabbitInfrastructure infrastructure;
    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;
    private final String applicationName;

    private final Class<T> interfaceClass;

    @SuppressWarnings("unchecked")
    public DynamicRabbitProxyFactoryBean(String interfaceClassName,
                                         RoutingKeyResolver routingKeyResolver,
                                         ExchangeNameResolver exchangeNameResolver,
                                         RabbitInfrastructure infrastructure,
                                         RabbitTemplate rabbitTemplate,
                                         AmqpAdmin amqpAdmin,
                                         String applicationName) throws ClassNotFoundException {
        this.routingKeyResolver = routingKeyResolver;
        this.exchangeNameResolver = exchangeNameResolver;
        this.infrastructure = infrastructure;
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
        this.applicationName = applicationName;

        // Load interface
        this.interfaceClass = (Class<T>) Class.forName(interfaceClassName);
    }

    @Override
    public T getObject() {
        RabbitEventPublisher annotation = interfaceClass.getAnnotation(RabbitEventPublisher.class);

        String exchange = annotation.exchange();
        ExchangeType exchangeType = annotation.exchangeType();
        String resolvedExchange = exchangeNameResolver.resolveExchangeName(exchange);
        String source = annotation.source().isEmpty() ? applicationName : annotation.source();

        if (annotation.autoCreate()) {
            infrastructure.createExchange(amqpAdmin, resolvedExchange, exchangeType);
            log.debug("Exchange auto-created: {} [{}]", resolvedExchange, exchangeType);
        } else {
            log.info("Exchange auto-creation disabled. Ensure exchange '{}' exists.", resolvedExchange);
        }

        return createProxy(resolvedExchange, exchangeType, source);
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private T createProxy(String exchange, ExchangeType exchangeType, String source) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] {interfaceClass},
                new PublisherInvocationHandler(rabbitTemplate, routingKeyResolver, exchange, exchangeType, source)
        );
    }

}