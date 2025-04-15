package az.ailab.lib.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for specific message routing keys.
 *
 * <p>This annotation is applied at the method level within a class annotated with
 * {@link RabbitEventListener} to define which method handles a specific routing key.</p>
 *
 * <p>The concept is similar to Spring MVC's {@code @RequestMapping} combined with
 * {@code @GetMapping}, {@code @PostMapping}, etc., but for message-driven architectures.
 * While {@code @RequestMapping} maps HTTP requests to handler methods based on URL patterns
 * and HTTP methods, {@code @RabbitEventHandler} maps RabbitMQ messages to handler methods
 * based on routing keys.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @RabbitEventListener(exchange = "user-events")  // Similar to @RestController
 * public class UserEventListener {
 *
 *     private final UserService userService;
 *
 *     @RabbitEventHandler(routingKey = "created", minConsumers = 3, maxConsumers = 8)  // Similar to @PostMapping
 *     public void handleUserCreated(EventMessage<UserCreatedEvent> event) {
 *         // Process user created event
 *         userService.create(event.payload());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "status.updated")  // Similar to @PutMapping
 *     public void handleUserStatusUpdated(EventMessage<UserStatusEvent> event) {
 *         // Process user status update
 *         notificationService.notifyStatusChange(event.payload());
 *     }
 *
 *     @RabbitEventHandler(routingKey = "cancelled")  // Similar to @DeleteMapping
 *     public void handleUserCancelled(EventMessage<UserDeletedEvent> event) {
 *         // Process user cancellation
 *         userService.delete(event.payload().getUserId());
 *     }
 * }
 * }</pre>
 *
 * @author tahmazovfarid
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RabbitEventHandler {

    /**
     * The routing key to handle.
     *
     * <p>This defines which messages from the exchange will be routed to this handler method,
     * similar to how URL paths work in {@code @RequestMapping}.</p>
     *
     * <p>It follows RabbitMQ routing key patterns, which depend on the exchange type:</p>
     * <ul>
     *   <li>For topic exchanges, you can use wildcards (* for one word, # for multiple words)</li>
     *   <li>For direct exchanges, the routing key must match exactly</li>
     *   <li>For fanout exchanges, the routing key is ignored</li>
     * </ul>
     *
     * @return the routing key
     */
    String routingKey();

    /**
     * The name of the queue to bind to this handler.
     *
     * <p>If not specified, a queue name will be generated automatically based on
     * the exchange name, routing key, and application name.</p>
     *
     * <p>The complete queue name is formed as follows:</p>
     * <pre>
     * [queuePrefix][queueName or exchangeName.routingKey]
     * </pre>
     *
     * <p>For example, if:</p>
     * <ul>
     *   <li>Queue prefix is "expertise.order-service."</li>
     *   <li>Exchange is "user-events"</li>
     *   <li>Routing key is "created"</li>
     *   <li>This parameter is not specified</li>
     * </ul>
     * <p>The resulting queue name will be "expertise.order-service.user-events.created"</p>
     *
     * <p>The queue prefix can be configured via the "spring.rabbitmq.config.queue-prefix" property.</p>
     *
     * @return the queue name (optional)
     */
    String queue() default "";

    /**
     * Minimum number of concurrent consumers for this handler's container.
     *
     * <p>Defines the minimum number of consumers that will process messages from the queue concurrently,
     * similar to how thread pool configurations work in web servers.</p>
     *
     * <p>If not specified (or set to 0), the default container factory settings will be used.</p>
     *
     * @return the minimum number of concurrent consumers
     */
    int minConsumers() default 2;

    /**
     * Maximum number of concurrent consumers for this handler's container.
     *
     * <p>Defines the maximum number of consumers that will process messages from the queue concurrently,
     * similar to how thread pool configurations work in web servers.</p>
     *
     * <p>If not specified (or set to 0), the value of minConsumers will be used.
     * If both are 0, the default container factory settings will be used.</p>
     *
     * @return the maximum number of concurrent consumers
     */
    int maxConsumers() default 5;

}