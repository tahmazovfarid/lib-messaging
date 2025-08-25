package az.ailab.lib.messaging.infra.annotation;

import az.ailab.lib.messaging.application.service.IdempotencyService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configuration annotation used to declare idempotency behavior for event handlers.
 *
 * <p>This annotation is not applied directly to a method anymore, but is embedded
 * within {@link RabbitEventHandler} as a configuration field:
 * <pre>
 * {@code
 * @RabbitEventHandler(
 *     idempotent = @Idempotent(ttlMs = 3_600_000)
 * )
 * public void handle(OrderCreatedEvent event) {
 *     // Your logic here
 * }
 * }
 * </pre>
 *
 * <p>When enabled, the {@link IdempotencyService} will store a processing marker
 * for each event's unique identifier (typically the aggregateId). If the same event
 * arrives again within the TTL duration, its execution will be skipped to prevent
 * duplicate side effects (e.g. double charges, double user creation).
 *
 * <h3>Fields:</h3>
 * <ul>
 *   <li>{@code enabled} - toggle for enabling/disabling idempotency (default: true)</li>
 *   <li>{@code ttlMs} - time in milliseconds after which the event can be processed again</li>
 * </ul>
 *
 * <h3>Default TTL:</h3>
 * <p>1 hour (3,600,000 ms)</p>
 *
 * @author tahmazovfarid
 * @since 1.2.1
 * @see RabbitEventHandler
 * @see IdempotencyService
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    boolean enabled() default true;

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
