package az.ailab.lib.messaging.config.annotation;

import az.ailab.lib.messaging.config.RabbitConfiguration;
import az.ailab.lib.messaging.config.RedissonConfiguration;
import az.ailab.lib.messaging.config.registrar.RabbitEventHandlerRegistrar;
import az.ailab.lib.messaging.infra.RabbitInfrastructure;
import az.ailab.lib.messaging.infra.annotation.RabbitEventListener;
import az.ailab.lib.messaging.infra.annotation.RabbitEventPublisher;
import az.ailab.lib.messaging.application.service.impl.RedisIdempotencyService;
import az.ailab.lib.messaging.config.registrar.RabbitPublisherRegistrar;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * Enables RabbitMQ messaging infrastructure in a Spring application.
 *
 * <p>This annotation imports all necessary RabbitMQ configuration and enables the automatic
 * discovery of {@link RabbitEventListener} and {@link RabbitEventPublisher} components.
 * It's similar to Spring's {@code @EnableJpaRepositories} or {@code @EnableWebMvc} annotations
 * in concept, but for RabbitMQ messaging.</p>
 *
 * <p>You can specify where to scan for annotated components via one or more of the following:</p>
 * <ul>
 *   <li>{@link #scanBasePackages()} — list of package names to scan.</li>
 * </ul>
 * <p>If none are provided, the package of the class annotated with {@code @EnableRabbitMessaging}
 * is used as the default base package.</p>
 * <p>When applied to a Spring {@code @Configuration} class, it:</p>
 * <ul>
 *   <li>Registers necessary beans for RabbitMQ connection and channel management</li>
 *   <li>Configures message converters for serialization/deserialization</li>
 *   <li>Sets up infrastructure for dynamic publisher and listener registration</li>
 *   <li>Applies configuration from properties or YAML files</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * @Configuration
 * @EnableRabbitMessaging({"az.ailab.msiam.event"})
 * public class RabbitConfig {
 *     // custom configuration
 * }
 * }</pre>
 *
 * <p>All configuration is done through application properties or YAML:</p>
 * <pre>{@code
 * # application.yml
 * spring:
 *   application:
 *     name: iam-service
 *   rabbitmq:
 *     host: localhost
 *     port: 5672
 *     username: tahmazovfarid
 *     password: tahmazovfarid
 *     config:
 *       queue-prefix: expertise.iam-service # {project_name}.{service_name}
 * }</pre>
 *
 * <p>After applying this annotation, you can create event publishers using {@link RabbitEventPublisher}
 * and event listeners using {@link RabbitEventListener} without additional configuration.</p>
 *
 * @since 1.0
 *
 * @author tahmazovfarid
 * @see RabbitInfrastructure
 * @see RabbitEventHandlerRegistrar
 * @see RabbitPublisherRegistrar
 * @see RabbitConfiguration
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
        RabbitEventHandlerRegistrar.class,
        RabbitPublisherRegistrar.class,
        RabbitConfiguration.class,
        RedissonConfiguration.class,
        RedisIdempotencyService.class,
})
public @interface EnableRabbitMessaging {

    /**
     * Alias for {@link #scanBasePackages()}.
     * Direct package names to scan for
     * {@link org.springframework.stereotype.Component} classes such as
     * {@link RabbitEventListener} and
     * {@link RabbitEventPublisher}.
     */
    @AliasFor("scanBasePackages")
    String[] value() default {};

    /**
     * Packages to scan for {@link RabbitEventListener} and {@link RabbitEventPublisher} components.
     * Defaults to the package of the annotated configuration class if empty.
     */
    @AliasFor("value")
    String[] scanBasePackages() default {};

}