package az.ailab.lib.messaging.core.enums;

/**
 * Defines the execution strategy for a given event handler method.
 * Determines how and when the event will be processed.
 *
 * <ul>
 *     <li>{@link #REAL_TIME} - The event is consumed and processed immediately as it arrives from the message broker.</li>
 *     <li>{@link #DEFERRED} - The event is first persisted (e.g., to a database or event store)
 *         and then processed asynchronously at a later time using a scheduled replay mechanism.
 *         This mode is suitable for scenarios involving ordering guarantees, dependency checks,
 *         or recovery from transient failures.</li>
 * </ul>
 *
 * <p>This enum is primarily used in the {@code @RabbitEventHandler} annotation
 * to configure how an event should be handled within the system.</p>
 *
 * Example usage:
 * <pre>
 * {@code
 * @RabbitEventHandler(
 *     routingKey = "user.created",
 *     mode = EventExecutionMode.DEFERRED,
 *     retryable = @Retryable(...)
 * )
 * public void handleUserCreated(UserCreatedEvent event) {
 *     ...
 * }
 * }
 * </pre>
 *
 * @since 1.3
 * @author tahmazovfarid
 */
public enum EventExecutionMode {

    /**
     * Process the event immediately as it arrives from the message broker.
     * This is the default behavior and is suitable for stateless or fast operations
     * that do not require ordering or dependency management.
     */
    REAL_TIME,

    /**
     * Defer processing of the event. The event is first stored (e.g., in a database)
     * and later replayed based on scheduling and retry logic.
     * Ideal for ensuring consistency across dependent operations and
     * recovering from transient failures.
     */
    DEFERRED

}