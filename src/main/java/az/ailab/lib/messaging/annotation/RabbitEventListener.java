package az.ailab.lib.messaging.annotation;

import az.ailab.lib.messaging.core.ExchangeType;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a RabbitMQ event listener.
 *
 * <p>This annotation is similar to Spring's {@code @RestController} in concept, but for
 * message-driven architecture. It defines a class that listens to events from a specific
 * exchange and contains methods to handle different routing keys.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @RabbitListener(exchange = "user-events")
 * @Slf4j
 * public class UserEventListener {
 *
 *     private final UserService userService;
 *
 *     @EventHandler(routingKey = "created")
 *     public void handleUserCreated(EventMessage<UserCreatedEvent> event) {
 *         // Process user created event
 *         userService.create(event);
 *         log.info("User created: {}", event.payload().getId());
 *     }
 *
 *     @EventHandler(routingKey = "updated")
 *     public void handleUserUpdated(EventMessage<UserUpdatedEvent> event) {
 *         // Process user updated event
 *         userService.update(event);
 *         log.info("User updated: {}", event.payload().getId());
 *     }
 * }
 * }</pre>
 *
 * @author tahmazovfarid
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RabbitEventListener {

    /**
     * The name of the exchange to listen to.
     *
     * <p>This exchange will be created if it doesn't exist and bound to queues
     * for each {@code @EventHandler} method in the class.</p>
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

}