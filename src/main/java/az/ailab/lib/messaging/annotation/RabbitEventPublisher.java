package az.ailab.lib.messaging.annotation;

import az.ailab.lib.messaging.core.ExchangeType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a publisher for RabbitMQ events.
 *
 * <p>This annotation is similar to Spring's {@code @FeignClient} in concept, but for
 * message publishing. It defines an interface for publishing events to a specific
 * exchange. The interface will be implemented automatically at runtime.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @RabbitPublisher(exchange = "user-events")
 * public interface UserEventPublisher {
 *
 *     @Routing(key = "created")
 *     void publishUserCreated(UserCreatedEvent event);
 *
 *     @Routing(key = "updated")
 *     void publishUserUpdated(UserUpdatedEvent event);
 *
 *     @Routing(key = "login")
 *     void publishUserLogin(UserLoginEvent event, @CorrelationId String correlationId);
 * }
 * }</pre>
 *
 * @author tahmazovfarid
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RabbitEventPublisher {

    /**
     * The name of the exchange to publish to.
     *
     * <p>This defines the exchange where all events published through this interface
     * will be sent.</p>
     *
     * @return the exchange name
     */
    String exchange();

    /**
     * The type of the exchange.
     *
     * @return the exchange type
     */
    ExchangeType exchangeType() default ExchangeType.TOPIC;

    /**
     * The source identifier for the messages.
     *
     * <p>This value will be used as the source field in the event message.
     * If not specified, the application name from Spring's configuration will be used.</p>
     *
     * @return the source identifier
     */
    String source() default "";

    /**
     * Indicates whether the exchange should be automatically created if it does not already exist.
     * <p>
     * When set to {@code true}, the exchange will be declared at application startup. This is useful
     * in development or dynamic environments where exchanges are not pre-provisioned.
     * In production, it's common to manage exchanges manually and set this to {@code false}
     * to avoid accidental creation or misconfiguration.
     *
     * @return {@code true} if the exchange should be auto-created; {@code false} otherwise
     */
    boolean autoCreate() default false;

}