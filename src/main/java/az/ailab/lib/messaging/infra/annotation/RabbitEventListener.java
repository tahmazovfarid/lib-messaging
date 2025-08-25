package az.ailab.lib.messaging.infra.annotation;

import az.ailab.lib.messaging.core.enums.ExchangeType;
import az.ailab.lib.messaging.infra.dto.EventMessage;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.NonNull;
import org.springframework.stereotype.Component;

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
 * @Slf4j
 * public class UserEventListener {
 *
 *     private final UserService userService;
 *
 *     @RabbitEventHandler(routingKey = "user.created")
 *     public void handleUserCreated(UserCreatedEvent event) {
 *         userService.create(event.payload());
 *         log.info("User {} creation synced.", event.getId());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "user.*.updated")
 *     public void handleUserUpdated(UserUpdatedEvent event) {
 *         userService.update(event.payload());
 *         log.info("User {} update synced.", event.getId());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "#.deleted")
 *     public void handleUserDeleted(EventMessage<UserDeletedEvent> event) {
 *         userService.delete(event.payload().getId());
 *         log.info("User {} delete synced.", event.payload().getId());
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
 * @author tahmazovfarid
 * @see RabbitEventHandler
 * @see RabbitEventPublisher
 * @see ExchangeType
 * @see EventMessage
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RabbitEventListener {

    /**
     * The name of the exchange to listen to.
     *
     * <p>This exchange will be created if it doesn't exist and bound to queues
     * for each {@code @EventHandler} method in the class.</p>
     *
     * @return the exchange name
     */
    @NonNull String exchange();

    /**
     * The type of the exchange.
     *
     * @return the exchange type
     */
    ExchangeType exchangeType() default ExchangeType.TOPIC;

    /**
     * Indicates whether the exchange should be automatically created if it does not already exist.
     * <p>When set to {@code true}, the exchange will be declared at application startup. This is useful
     * in development or dynamic environments where exchanges are not pre-provisioned.
     * In production, it's common to manage exchanges manually and set this to {@code false}
     * to avoid accidental creation or misconfiguration.</p>
     *
     * @return {@code true} if the exchange should be auto-created; {@code false} otherwise
     */
    boolean autoCreate() default false;

    /**
     * Dead letter exchange suffix
     */
    String dlxSuffix() default ".dlx";

}