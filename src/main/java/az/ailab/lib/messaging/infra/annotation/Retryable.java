package az.ailab.lib.messaging.infra.annotation;

import az.ailab.lib.messaging.infra.RabbitInfrastructure;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import az.ailab.lib.messaging.infra.adapter.RealtimeListenerAdapter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;

/**
 * Defines retry behavior for a message handler method annotated with {@link RabbitEventHandler}.
 *
 * <p>This annotation configures how and when a failed message should be retried — including:
 * retry enablement, delay between retries, maximum attempts, and exception-based filtering.</p>
 *
 * <h2>Usage Context</h2>
 * <p>Used within {@link RabbitEventHandler#retryable()} to control failure handling logic.</p>
 *
 * <h2>Execution Mode Awareness</h2>
 * Behavior of this annotation changes depending on {@link RabbitEventHandler#mode()}:
 * <ul>
 *   <li><b>{@code REAL_TIME}</b> – Retry is implemented via RabbitMQ infrastructure:
 *     <ul>
 *       <li>Retry queues (with TTL)</li>
 *       <li>Dead-letter routing to original queue</li>
 *       <li>DLQ routing on exhaustion (if enabled)</li>
 *     </ul>
 *   </li>
 *   <li><b>{@code DEFERRED}</b> – Retry is handled by the library’s internal scheduler loop:
 *     <ul>
 *       <li>Failed events are kept in the database (event store)</li>
 *       <li>Reprocessed after fixed delay</li>
 *       <li>Retry attempts are tracked at application-level</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Retry Attempts Semantics</h3>
 * <ul>
 *   <li>{@code maxAttempts = 0} → no retry (fail immediately)</li>
 *   <li>{@code maxAttempts > 0} → bounded retries</li>
 *   <li>{@code maxAttempts = -1} → infinite retry (only supported in DEFERRED mode)</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @RabbitEventHandler(
 *     routingKey = "order.created",
 *     mode = EventExecutionMode.REAL_TIME,
 *     retryable = @Retryable(
 *         enabled = true,
 *         maxAttempts = 5,
 *         fixedDelayMs = 3000,
 *         retryFor = {TransientException.class},
 *         notRetryFor = {ValidationException.class}
 *     )
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     orderService.process(event);
 * }
 * }</pre>
 *
 * @see RabbitEventHandler
 * @see EventExecutionMode
 * @see RabbitInfrastructure
 * @see RealtimeListenerAdapter
 * @since 1.2
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    /**
     * Whether retry behavior is enabled for the annotated handler.
     *
     * <p>When set to {@code false}, the message will not be retried under any circumstance.
     * In REAL_TIME mode, it will be immediately dead-lettered if {@link RabbitEventHandler#enableDeadLetterQueue()}
     * is enabled. In DEFERRED mode, the event will be marked as {@code FAILED} upon first failure.</p>
     *
     * @return true to enable retry logic; false to skip retries entirely
     */
    boolean enabled() default true;

    /**
     * Initial delay to apply before the first retry attempt.
     *
     * <p>This value is particularly relevant in DEFERRED mode, where the event scheduler will
     * wait this duration before the first replay attempt. In REAL_TIME mode, it maps to the
     * TTL of the first retry queue if broker TTL queues are used.</p>
     *
     * @return initial delay before first retry (default: 5000 ms)
     */
    long initDelay() default 3_000;

    /**
     * Fixed delay between consecutive retry attempts.
     *
     * <p>Controls how long to wait between retries after the first attempt.
     * In DEFERRED mode, this governs the replay scheduler's wait between failures.
     * In REAL_TIME mode, this becomes the TTL on retry queues (e.g., backoff queues).</p>
     *
     * @return delay between retry attempts (default: 15000 ms)
     */
    long fixedDelay() default 15_000;

    /**
     * Time unit for {@link #initDelay()} and {@link #fixedDelay()}.
     *
     * <p>Defaults to milliseconds. All timing values in this annotation are interpreted
     * using this time unit.</p>
     *
     * @return time unit used for all delay fields
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * Maximum number of retry attempts allowed for this handler.
     *
     * <p>Behavior differs by execution mode:</p>
     * <ul>
     *   <li><b>REAL_TIME:</b>
     *     <ul>
     *       <li>0 → Retry disabled.</li>
     *       <li>&gt; 0 → Retry up to N times using broker-managed retry queues.</li>
     *       <li><b>-1 is technically allowed but <u>not recommended</u></b> – behavior becomes broker-specific (often means infinite requeue or DLQ-suppression),
     *       which may result in message flooding, endless loops, or broker instability. Always prefer bounded retries.</li>
     *     </ul>
     *   </li>
     *   <li><b>DEFERRED:</b>
     *     <ul>
     *       <li>-1 → Retry indefinitely (until success or manual intervention).</li>
     *       <li>&gt;= 0 → Retry up to the specified number of times, then mark event as {@code FAILED}.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @return the number of retry attempts allowed before the message is considered failed
     */
    long maxAttempts() default 3;

    /**
     * Exceptions that are eligible for retry.
     *
     * <p>If empty, all exceptions are assumed to be retryable by default. If specified,
     * only exceptions listed here will trigger a retry attempt.</p>
     *
     * <p>Ignored if the thrown exception also appears in {@link #notRetryFor()} —
     * the exclusion list takes precedence.</p>
     *
     * @return array of retryable exception types
     */
    @NonNull Class<? extends Throwable>[] retryFor() default {Exception.class};

    /**
     * Exceptions that should never be retried.
     *
     * <p>If an exception type is listed here, the retry will be skipped even if it appears
     * in {@link #retryFor()}. Use this to protect against invalid business logic (e.g., validation failures).</p>
     *
     * @return array of exception types to skip retry for
     */
    @NonNull Class<? extends Throwable>[] notRetryFor() default {};

    /**
     * Suffix used to construct the retry exchange name.
     *
     * <p>The full retry exchange name is generated by appending this suffix to the original
     * exchange name. For example, for an exchange named <code>orders</code>, the retry exchange becomes
     * <code>orders.retry</code> if this value is ".retry".</p>
     *
     * @return suffix string for retry exchange naming (default: ".retry")
     */
    String retryXSuffix() default ".retry";

    /**
     * Suffix used to construct the retry queue name.
     *
     * <p>The retry queue is named by appending this suffix to the original queue name.
     * Example: <code>orders.created</code> → <code>orders.created.retry</code>.</p>
     *
     * <p>This naming strategy is used for managing broker-level TTL queues used in REAL_TIME retry flow.</p>
     *
     * @return suffix string for retry queue naming (default: ".retry")
     */
    String retryQSuffix() default ".retry";

}