package az.ailab.lib.messaging.infra.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a single header entry to be added to the message.
 * <p>The parameter value will be converted to a string and sent under the specified
 * header name.</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @RabbitEventPublisher
 * public interface UserPublisher {
 *     @Routing(key = "created")
 *     void publishEvent(@Paylaod InstitutionCreatedEvent payload
 *                       @EventHeader("X-User-Id") Long userId);
 * }
 * }</pre>
 *
 * <p>Here, the <code>userId</code> value will be sent as the
 * <code>X-User-Id</code> header in the message.</p>
 *
 * @since 1.0.2
 * @author tahmazovfarid
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHeader {

    /**
     * The name of the header key to which the parameter value will be mapped.
     *
     * @return header key name
     */
    String name();

}
