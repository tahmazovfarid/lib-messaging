package az.ailab.lib.messaging.infra.annotation;

import az.ailab.lib.messaging.core.annotation.DependsOnEvent;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.NonNull;

/**
 * Marks a method as a message-driven event handler bound to a RabbitMQ routing key.
 * <p>
 * This annotation is used on methods inside a class annotated with {@link RabbitEventListener}.
 * Each method-level handler maps one (or more, depending on exchange binding patterns) Rabbit message
 * routing key to domain-specific processing logic.
 * </p>
 *
 * <h2>Conceptual Analogy</h2>
 * Think of {@code @RabbitEventListener} as analogous to {@code @RestController}, and
 * {@code @RabbitEventHandler} as the messaging equivalent of {@code @RequestMapping} /
 * {@code @GetMapping}/{@code @PostMapping}. Instead of HTTP paths, you bind to Rabbit routing keys.
 * <hr/>
 *
 * <h2>Execution Modes & Processing Model</h2>
 * Controlled by {@link #mode()}:
 * <ul>
 *   <li><b>{@link EventExecutionMode#REAL_TIME}</b> – Messages are consumed from RabbitMQ and
 *       delivered to the handler method immediately. Retry semantics (see {@link #retryable()})
 *       are applied using broker/container-level retry/backoff. No event-store persistence is done
 *       by the framework (unless separately configured).</li>
 *   <li><b>{@link EventExecutionMode#DEFERRED}</b> – Messages are <i>not</i> processed immediately.
 *       Instead, they are persisted to an event store (DB) and later processed asynchronously by the
 *       library’s replay engine. This enables ordered execution, cross-event dependency checks,
 *       long-lived retry policies, and operational replay after failures or outages.</li>
 * </ul>
 *
 * <h3>Retry Behavior by Mode</h3>
 * <table border="10" cellpadding="4" cellspacing="10">
 *   <thead>
 *     <tr>
 *       <th nowrap>Mode</th>
 *       <th nowrap>When Handler Runs</th>
 *       <th nowrap>Where Retry Happens</th>
 *       <th nowrap>maxAttempts Semantics</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td nowrap>REAL_TIME</td>
 *       <td>Immediately on message delivery from Rabbit</td>
 *       <td>Broker/container retry (requeue, backoff, DLQ)</td>
 *       <td>0 = no retry; &gt;0 = bounded attempts; -1 ignored (treated as broker-specific infinite/always-requeue policy if supported)</td>
 *     </tr>
 *     <tr>
 *       <td>DEFERRED</td>
 *       <td>Persisted first; replayed by scheduler</td>
 *       <td>Library-level replay loop driven by {@link Retryable#initDelay()} /
 *           {@link Retryable#fixedDelay()}</td>
 *       <td>-1 = retry forever until success or manual intervention; &gt;=0 = bounded replays</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <p>In DEFERRED mode, broker delivery succeeds once the message is stored; actual domain handling
 * can be retried safely without message redelivery storms.</p>
 *
 * <hr/>
 * <h2>Dependencies</h2>
 * Declared via {@link #dependencies()} and composed of {@link DependsOnEvent} annotations.
 * Dependencies let you express causal ordering across handlers. Example: a
 * <code>UserCreated</code> event must not be processed until the referenced <code>RoleCreated</code>
 * and <code>StructureCreated</code> events have completed successfully.
 *
 * <p>Each dependency declares:</p>
 * <ul>
 *   <li><b>name</b> – The identifier of the target event this handler depends on.
 *       It must follow the format <code>exchange_name.routing_key</code> (e.g., <code>user-events.created</code>).
 *       This value should uniquely point to the upstream event handler whose successful execution
 *       must precede this one. The framework uses this identifier during dependency resolution —
 *       no manual handler naming is needed.</li>
 *
 *   <li><b>key</b> – The field name in the current event’s payload whose value represents
 *       the aggregate ID of the dependency. For instance, <code>"roleId"</code> or <code>"structureId"</code>
 *       in a UserCreated event.</li>
 *
 *   <li><b>skipAggregates</b> – List of aggregate identifiers (as strings) for which this dependency
 *       should be ignored. Useful for system bootstrap cases, static reference entities, or migration flows
 *       where dependency enforcement is not required.</li>
 * </ul>
 *
 * <p>Dependency Resolution Flow (DEFERRED mode):</p>
 * <ol>
 *   <li>Message arrives → persisted to event store.</li>
 *   <li>Replay engine loads PENDING events.</li>
 *   <li>For each dependency: extract <code>key</code> value from payload; look up target handler
 *       {@code name} + aggregateId in processed event index.</li>
 *   <li>If <em>all</em> dependencies satisfied → invoke handler.</li>
 *   <li>If any missing → leave event PENDING; retry later per {@link #retryable()} policy.</li>
 * </ol>
 *
 * <hr/>
 *
 * <h2>Queue Binding</h2>
 * Use {@link #routingKey()} to bind. {@link #queue()} overrides default queue naming. By default,
 * the framework composes a queue name from configured prefix + exchange + routing key.
 * <hr/>
 *
 * <h2>Dead-Letter Behavior</h2>
 * Controlled by {@link #enableDeadLetterQueue()} and {@link #dlqSuffix()}.
 * When enabled and retries are exhausted (REAL_TIME mode) or replay attempts exceeded (DEFERRED mode),
 * the message is routed to a DLQ named <code>[queueName][dlqSuffix]</code>.
 * <hr/>
 *
 * <p>Usage example:</p>
 *
 * <h3>Example: REAL_TIME Mode (Immediate Execution)</h3>
 * <p>In this example, the handler is invoked immediately upon message delivery.
 * Retry is handled by the broker or container.</p>
 * <pre>{@code
 * @RabbitEventListener(exchange = "user-events", autoCreate = true)  // Similar to @RestController
 * public class UserEventListener {
 *
 *     private final UserService userService;
 *
 *     @RabbitEventHandler(
 *         routingKey = "created",
 *         minConsumers = 3,
 *         maxConsumers = 6,
 *         mode = EventExecutionMode.REAL_TIME, // default value
 *         retryable = @Retryable(
 *             enabled = true, // default value
 *             delayMs = 5000,
 *             maxAttempts = 4,
 *             retryFor = {TransientException.class},
 *             noRetryFor = {IllegalArgumentException.class}
 *         ),
 *         dlqSuffix = ".dlq" // default value
 *     )
 *     public void handleUserCreated(EventMessage<UserCreatedEvent> event) {
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
 * <p>This configuration will:</p>
 * <ul>
 *   <li>Listen for messages with routing key <code>created</code> on the <code>user-events</code> exchange</li>
 *   <li>Use 3–6 concurrent consumers for this specific handler</li>
 *   <li>Retry up to 4 times with 5s delay on transient errors</li>
 *   <li>Route to <code>[queue].dlq</code> if all retries are exhausted or a non-retryable exception is thrown</li>
 * </ul>
 *
 * <h3>Example: DEFERRED Mode (<b>NOT<b/> Immediate Execution)</h3>
 * <pre>{@code
 * @RabbitEventListener(exchange = "user-events", autoCreate = true)
 * public class UserEventListener {
 *
 *   private final UserService userService;
 *
 * @RabbitEventHandler(
 *     routingKey = "user.created",
 *     mode = EventExecutionMode.DEFERRED,
 *     retryable = @Retryable(...)
 *     dependencies = {
 *         @Dependency(name = "ROLE_CREATED", key = "roleId", skipAggregates = {1, 2}),
 *         @Dependency(name = "STRUCTURE_CREATED", key = "structureId")
 *     }
 * )
 *   public void handleUserCreated(EventMessage<UserCreatedEvent> event) {
 *       userService.create(event.payload());
 *   }
 * }
 * }</pre>
 *
 * <hr/>
 *
 * <h2>Naming Guidelines</h2>
 * <ul>
 *   <li><b>{@link #dependencies()}'s name()</b> MUST be targeted exchange_name.routing_key</li>
 *   <li>Dependency resolution depends on this value; changing it breaks upstream references.</li>
 * </ul>
 *
 * @author tahmazovfarid
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RabbitEventHandler {

    /**
     * The routing key to bind for message selection on the declared exchange.
     *
     * <p>Behavior depends on exchange type:</p>
     * <ul>
     *   <li><b>topic</b>: supports wildcards (<code>*</code> = one word, <code>#</code> = many)</li>
     *   <li><b>direct</b>: must match exactly</li>
     *   <li><b>fanout</b>: ignored (all bound queues receive messages)</li>
     *   <li><b>headers</b>: unsupported
     * </ul>
     *
     * @return the routing key pattern this handler consumes
     */
    @NonNull String routingKey();

    /**
     * Controls how the event is executed: immediate (REAL_TIME) vs persisted + replayed (DEFERRED).
     * <p>See class-level Javadoc section <b>Execution Modes &amp; Processing Model</b> for details.</p>
     */
    @NonNull EventExecutionMode mode() default EventExecutionMode.REAL_TIME;

    /**
     * Explicit queue name override.
     * <p>If empty, the framework builds a queue name using:</p>
     * <pre>[queuePrefix].[exchangeName].[routingKey]</pre>
     * <p>where {@code queuePrefix} is configurable (e.g. {@code spring.rabbitmq.config.queue-prefix}).</p>
     *
     * @return queue name override or empty for auto-generated
     */
    String queue() default "";

    /**
     * Minimum number of concurrent consumers (listener threads) assigned to this handler's queue container.
     * <p>
     * This controls the baseline parallelism for message consumption.
     * If set to {@code <= 0}, the container's default value will be used.
     * </p>
     * <p>Recommended: keep ≥1 for production queues.</p>
     */
    int minConsumers() default 1;

    /**
     * Maximum number of concurrent consumers (listener threads) allowed for this handler's queue container.
     * <p>
     * Defines the upper limit of dynamic thread scaling for this queue based on load.
     * If set to {@code <= 0}, it will fall back to {@link #minConsumers()}.
     * If both values are {@code <= 0}, container-level defaults apply.
     * </p>
     * <p>Recommended: use higher values (e.g., 10–20) for bursty or high-throughput queues.</p>
     */
    int maxConsumers() default 5;

    /**
     * Number of unacknowledged messages that can be delivered to each consumer at once (prefetch count).
     * <p>
     * Controls the message flow rate from RabbitMQ to the consumer.
     * A lower value (e.g., 1–3) provides fair dispatching and prevents consumer overload;
     * a higher value increases throughput but may lead to resource contention.
     * </p>
     * <p>Default is 3. Set {@code <= 0} to use container default.</p>
     */
    int prefetch() default 3;

    /**
     * Retry / replay policy.
     *
     * <h6>REAL_TIME Mode</h6>
     * Applied at message-consumption time. The container/broker will attempt redelivery
     * up to {@code maxAttempts}. Delay/backoff defined by {@code delayMs} (or framework backoff policy).
     * Non-retryable exceptions (see {@link Retryable#notRetryFor()}) are routed directly to DLQ
     * if {@link #enableDeadLetterQueue()} is true.
     *
     * <h6>DEFERRED Mode</h6>
     * The message is persisted first. The library’s replay scheduler applies the retry policy:
     * <ul>
     *   <li>{@code initDelayMs} – first processing window after initial store.</li>
     *   <li>{@code fixedDelayMs} – delay between replay attempts.</li>
     *   <li>{@code maxAttempts = -1} – retry forever (recommended for dependency-driven workflows).</li>
     * </ul>
     * Retry filters still apply: if exception type matches {@link Retryable#notRetryFor()}, the
     * event is marked FAILED and (optionally) DLQ’d.
     *
     * @return retry configuration for this handler
     */
    Retryable retryable() default @Retryable(enabled = false);

    Idempotent idempotent() default @Idempotent;

    /**
     * Declarative inter-event dependencies (causal ordering).
     *
     * <p>Each {@link DependsOnEvent} entry expresses: “Before invoking <i>this</i> handler,
     * ensure that handler {@code name} has successfully processed an event whose aggregate ID
     * matches the value extracted from the current payload field {@code key}.”</p>
     *
     * <p>Dependency resolution is enforced only in {@link EventExecutionMode#DEFERRED} mode.
     * In {@link EventExecutionMode#REAL_TIME}, declared dependencies are ignored (but may be logged).</p>
     *
     * @return dependency metadata array
     */
    DependsOnEvent[] dependencies() default {};

    /**
     * Suffix appended to the handler queue name when dead-letter routing is enabled.
     * <p>Final DLQ name: {@code [queueName]} + {@link #dlqSuffix()}.</p>
     */
    String dlqSuffix() default ".dlq";

    /**
     * Enable routing of failed / exhausted messages to a Dead Letter Queue.
     * <p>Triggers when retry attempts are exhausted (REAL_TIME) or when replay attempts reach
     * {@code maxAttempts} or a non-retryable exception is thrown (DEFERRED).</p>
     */
    boolean enableDeadLetterQueue() default true;

}