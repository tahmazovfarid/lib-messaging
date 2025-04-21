package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.core.RabbitInfrastructure;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitInfrastructureConfig {

    @Bean
    public RabbitInfrastructure rabbitInfrastructure() {
        return new RabbitInfrastructure();
    }

}
