package az.ailab.lib.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method in a {@link RabbitEventPublisher} interface with routing information.
 *
 * <p>This annotation is similar to Spring's {@code @RequestMapping} in concept, but for
 * message publishing. It defines the routing key for messages published by this method.</p>
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
     * <p>When true, the publisher will wait for a confirmation from the broker
     * that the message has been received.</p>
     *
     * @return true if publisher confirms should be used, false otherwise
     */
    boolean confirm() default false;

}