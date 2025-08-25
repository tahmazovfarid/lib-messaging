package az.ailab.lib.messaging.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as the correlation ID for a published message.
 * 
 * <p>This annotation allows specifying a correlation ID when publishing messages,
 * which is useful for tracking related messages across services.</p>
 *
 * @since 1.0
 * @author tahmazovfarid
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CorrelationId {
    // Marker annotation, no attributes needed
}