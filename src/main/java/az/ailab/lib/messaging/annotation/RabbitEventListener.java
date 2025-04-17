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
 * <p>This annotation designates a class as a component that listens for messages from a specific
 * RabbitMQ exchange. It's conceptually similar to Spring's {@code @RestController} annotation,
 * but for message-driven architectures instead of HTTP requests.</p>
 *
 * <p>Classes annotated with {@code @RabbitEventListener} should contain methods annotated with
 * {@link RabbitEventHandler} to define handlers for specific routing keys. The infrastructure
 * will automatically:</p>
 * <ul>
 *   <li>Create queues for each handler method</li>
 *   <li>Bind the queues to the exchange with the appropriate routing keys</li>
 *   <li>Set up message listeners that dispatch messages to the handler methods</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @RabbitEventListener(exchange = "user-events", exchangeType = ExchangeType.TOPIC)
 * @Component
 * @Slf4j
 * public class UserEventListener {
 *
 *     private final UserService userService;
 *
 *     @RabbitEventHandler(routingKey = "user.created")
 *     public void handleUserCreated(EventMessage<UserCreatedEvent> event) {
 *         userService.create(event.payload());
 *         log.info("User created: {}", event.payload().getId());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "user.*.updated")
 *     public void handleUserUpdated(EventMessage<UserUpdatedEvent> event) {
 *         userService.update(event.payload());
 *         log.info("User updated: {}", event.payload().getId());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "#.deleted")
 *     public void handleUserDeleted(EventMessage<UserDeletedEvent> event) {
 *         userService.delete(event.payload().getId());
 *         log.info("User deleted: {}", event.payload().getId());
 *     }
 * }
 * }</pre>
 *
 * <p>In the example above:</p>
 * <ul>
 *   <li>A topic exchange named "user-events" will be created</li>
 *   <li>Three queues will be created, one for each handler method</li>
 *   <li>The queues will be bound to the exchange with routing keys "user.created", "user.*.updated", and "#.deleted"</li>
 *   <li>Messages published to the exchange will be routed to the appropriate handler method based on the routing key</li>
 * </ul>
 *
 * @see RabbitEventHandler
 * @see RabbitEventPublisher
 * @see ExchangeType
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