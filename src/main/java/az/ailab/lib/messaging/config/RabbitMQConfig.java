package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.annotation.EnableRabbitMessaging;
import az.ailab.lib.messaging.core.RabbitMQEventHandlerRegistrar;
import az.ailab.lib.messaging.core.RabbitMQEventListenerRegistrar;
import az.ailab.lib.messaging.core.RabbitMQPublisherRegistrar;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration class that sets up all the necessary beans for RabbitMQ messaging.
 *
 * <p>This class is imported by the {@link EnableRabbitMessaging}
 * annotation to configure the RabbitMQ infrastructure.</p>
 *
 * @author tahmazovfarid
 */
@Configuration
@EnableRabbit
@Import({
        RabbitMQEventListenerRegistrar.class,
        RabbitMQEventHandlerRegistrar.class,
        RabbitMQPublisherRegistrar.class,
        RabbitMQExtendedConfiguration.class
})
public interface RabbitMQConfig {

    // This is a marker interface for the @Import annotation

}