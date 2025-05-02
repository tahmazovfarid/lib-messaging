package az.ailab.lib.messaging.core.listener.annotation;

import az.ailab.lib.messaging.core.RabbitInfrastructure;
import az.ailab.lib.messaging.core.listener.adapter.ContainerListenerAdapter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lombok.NonNull;

/**
 * Defines retry behavior for a message handler method annotated with {@link RabbitEventHandler}.
 *
 * <p>This annotation allows you to configure native retry and dead-letter routing logic in RabbitMQ.
 * It is used within {@link RabbitEventHandler#retry()} to indicate whether a failed message
 * should be retried, how many times it should be attempted, what delay to apply between retries,
 * and which exceptions are considered retryable.</p>
 *
 * <p>Retry attempts are managed through dedicated retry queues with TTL and dead-letter routing
 * back to the original processing queue. After exceeding the maxAttempts, the message may be routed
 * to a dead-letter queue (DLQ) based on the {@link RabbitEventHandler#enableDeadLetterQueue()} flag.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>{@code
 * @RabbitEventHandler(routingKey = "order.created",
 *     retry = @Retry(
 *         enabled = true,
 *         delayMs = 3000,
 *         maxAttempts = 5,
 *         retryFor = {TransientException.class}
 *         // or 'notRetryFor = {FatalBusinessException.class}'
 *     )
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     orderService.process(event);
 * }
 * }</pre>
 *
 * @see RabbitEventHandler
 * @see RabbitInfrastructure
 * @see ContainerListenerAdapter
 * @since 1.1
 * @author tahmazovfarid
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retry {

    /**
     * Whether retry logic is enabled for the annotated handler.
     * <p>When disabled, failed messages will be routed directly to the DLQ if configured.</p>
     *
     * @return true to enable retry, false to disable it
     */
    boolean enabled() default true;

    /**
     * Delay (in milliseconds) between retry attempts.
     * <p>Each retry attempt is delayed using a TTL-based retry queue before the message
     * is dead-lettered back to the original queue for re-processing.</p>
     *
     * @return the delay in milliseconds before retrying
     */
    long delayMs() default 5_000;

    /**
     * Maximum number of retry attempts before giving up and routing the message to the DLQ.
     *
     * @return the total number of retry attempts allowed
     */
    long maxAttempts() default 3;

    /**
     * List of exception types that are considered retryable.
     * <p>If empty, all {@link Exception} types are treated as retryable by default.</p>
     *
     * @return array of retryable exception classes
     */
    @NonNull Class<? extends Throwable>[] retryFor() default {Exception.class};

    /**
     * List of exception types that are explicitly excluded from retries.
     * <p>This list takes precedence over {@link #retryFor()} — if an exception is present in both,
     * it is treated as non-retryable.</p>
     *
     * @return array of non-retryable exception classes
     */
    @NonNull Class<? extends Throwable>[] notRetryFor() default {};

    /**
     * Suffix to use when naming the retry exchange.
     * <p>This is appended to the original exchange name to generate the retry exchange name.</p>
     * Example: "orders" → "orders.retry"
     *
     * @return the retry exchange suffix
     */
    String retryXSuffix() default ".retry";

    /**
     * Suffix to use when naming the retry queue.
     * <p>This is appended to the original queue name to generate the retry queue name.</p>
     * Example: "orders.created" → "orders.created.retry"
     *
     * @return the retry queue suffix
     */
    String retryQSuffix() default ".retry";

}