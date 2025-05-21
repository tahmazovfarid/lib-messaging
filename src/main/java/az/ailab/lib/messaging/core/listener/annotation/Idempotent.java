package az.ailab.lib.messaging.core.listener.annotation;

import az.ailab.lib.messaging.core.listener.idempotency.IdempotencyService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for idempotent execution, ensuring that duplicate invocations
 * with the same business identifier are ignored.
 * <p>
 * When applied, the annotated method uses an {@link IdempotencyService}
 * to store a marker for each unique identifier. If the marker already exists
 * and has not expired, the method invocation is skipped to prevent duplicate
 * side effects.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @Idempotent(ttlMs = 3_600_000) // 1 hour TTL
 * public void processOrder(OrderCreatedEvent event) {
 *     // handle event
 * }
 * }
 *
 * @author tahmazovfarid
 * @since 1.2.1
 * @see IdempotencyService
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Time-to-live in milliseconds for the idempotency record.
     * <p>
     * After this period, the same identifier may be processed again.
     * Defaults to 1 hour (3_600_000 ms).
     *
     * @return the TTL duration in milliseconds
     */
    long ttlMs() default 3_600_000;

}
