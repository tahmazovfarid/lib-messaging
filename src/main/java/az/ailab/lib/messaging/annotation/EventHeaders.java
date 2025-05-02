package az.ailab.lib.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a map of headers to merge into the outgoing message.
 * <p>When publishing an event, any entries in the provided map will be added to the
 * message headers alongside the standard headers (exchange type, routing key, etc.).</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * @RabbitEventPublisher
 * public interface UserPublisher {
 *     @Routing(key = "created")
 *     void publishEvent(@Paylaod InstitutionCreatedEvent payload
 *                       @EventHeaders Map<String, Object> extraHeaders);
 * }
 * }</pre>
 *
 * <p>In this example, all entries in <code>extraHeaders</code> will be included
 * in the AMQP message headers when <code>publishEvent</code> is called.</p>
 *
 * @since 1.0.2
 * @author tahmazovfarid
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventHeaders {

}
