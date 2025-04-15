package az.ailab.lib.messaging.annotation;

import az.ailab.lib.messaging.config.RabbitMQConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables RabbitMQ messaging capabilities in a Spring application.
 * This annotation should be applied to a configuration class to import all necessary
 * RabbitMQ infrastructure components.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @EnableRabbitMessaging
 * @SpringBootApplication
 * public class OrderServiceApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(OrderServiceApplication.class, args);
 *     }
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
 * @author tahmazovfarid
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RabbitMQConfig.class)
public @interface EnableRabbitMessaging {
    // No parameters needed as all configuration comes from properties
}