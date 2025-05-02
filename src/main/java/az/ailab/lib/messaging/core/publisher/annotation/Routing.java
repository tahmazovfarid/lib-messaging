package az.ailab.lib.messaging.core.publisher.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in a {@link RabbitEventPublisher} interface with routing information.
 *
 * <p>This annotation is similar to Spring's {@code @RequestMapping} in concept, but for
 * message publishing. It defines the exchange routing key for messages published by this method,
 * and optionally enables publisher confirms with a configurable timeout.</p>
 *
 * <p>If {@link #confirm()} is {@code true}, the publisher will wait up to
 * {@link #timeout()} milliseconds for the broker to ACK/NACK the message.</p>
 *
 * @author tahmazovfarid
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Routing {

    /**
     * The routing key for the published message.
     *
     * @return the routing key
     */
    String key();

    /**
     * Indicates whether publishing confirms should be used for this method.
     *
     * <p>When {@code true}, the publisher will wait for a confirmation from the broker
     * that the message has been received.</p>
     *
     * @return {@code true} if publisher confirms should be used, {@code false} otherwise
     */
    boolean confirm() default false;

    /**
     * Maximum time in milliseconds to wait for a publisher confirm from the broker.
     * Only applies if {@link #confirm()} is {@code true}.
     *
     * @since 1.2
     * @return timeout in milliseconds
     */
    long timeout() default 1_000L;

}