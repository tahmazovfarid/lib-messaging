package az.ailab.lib.messaging.annotation;

import az.ailab.lib.messaging.config.RabbitMQConfig;
import az.ailab.lib.messaging.core.RabbitMQInfrastructure;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables RabbitMQ messaging infrastructure in a Spring application.
 *
 * <p>This annotation imports all necessary RabbitMQ configuration and enables the automatic
 * discovery of {@link RabbitEventListener} and {@link RabbitEventPublisher} components.
 * It's similar to Spring's {@code @EnableJpaRepositories} or {@code @EnableWebMvc} annotations
 * in concept, but for RabbitMQ messaging.</p>
 *
 * <p>When applied to a Spring {@code @Configuration} class, it:</p>
 * <ul>
 *   <li>Registers necessary beans for RabbitMQ connection and channel management</li>
 *   <li>Configures message converters for serialization/deserialization</li>
 *   <li>Sets up infrastructure for dynamic publisher and listener registration</li>
 *   <li>Applies configuration from properties or YAML files</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @Configuration
 * @EnableRabbitMessaging
 * public class RabbitConfiguration {
 *     // Additional custom configuration if needed
 * }
 * }</pre>
 *
 * <p>All configuration is done through application properties or YAML:</p>
 * <pre>{@code
 * # application.yml
 * spring:
 *   application:
 *     name: user-service
 *   rabbitmq:
 *     host: localhost
 *     port: 5672
 *     username: tahmazovfarid
 *     password: tahmazovfarid
 *     config:
 *       queue-prefix: expertise.user-service # {project_name}.{service_name}
 *       queue-durable: true
 *       exchange-durable: true
 * }</pre>
 *
 * <p>After applying this annotation, you can create event publishers using {@link RabbitEventPublisher}
 * and event listeners using {@link RabbitEventListener} without additional configuration.</p>
 *
 * @author tahmazovfarid
 * @see RabbitEventPublisher
 * @see RabbitEventListener
 * @see RabbitMQConfig
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(value = {RabbitMQInfrastructure.class, RabbitMQConfig.class})
@CheckProxyClassGeneration
public @interface EnableRabbitMessaging {
    // No parameters needed as all configuration comes from properties
}