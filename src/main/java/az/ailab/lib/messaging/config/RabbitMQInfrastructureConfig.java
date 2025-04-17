package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.core.RabbitMQInfrastructure;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQInfrastructureConfig {

    @Bean
    public RabbitMQInfrastructure rabbitMQInfrastructure() {
        return new RabbitMQInfrastructure();
    }

}
