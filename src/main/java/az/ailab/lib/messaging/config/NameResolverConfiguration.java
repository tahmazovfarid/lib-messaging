package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.config.properties.RabbitExtendedProperties;
import az.ailab.lib.messaging.infra.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.infra.resolver.QueueNameResolver;
import az.ailab.lib.messaging.infra.resolver.RoutingKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class NameResolverConfiguration {

    private final RabbitExtendedProperties properties;

    /**
     * Creates a QueueNameResolver for resolving queue names based on application context and configuration.
     *
     * @return the queue name resolver
     */
    @Bean
    public QueueNameResolver queueNameResolver() {
        return new QueueNameResolver(properties.getQueuePrefix());
    }

    /**
     * Creates an ExchangeNameResolver for resolving exchange names.
     *
     * @return the exchange name resolver
     */
    @Bean
    public ExchangeNameResolver exchangeNameResolver() {
        return new ExchangeNameResolver();
    }

    /**
     * Creates a RoutingKeyResolver for resolving routing keys.
     *
     * @return the routing key resolver
     */
    @Bean
    public RoutingKeyResolver routingKeyResolver() {
        return new RoutingKeyResolver();
    }

}
