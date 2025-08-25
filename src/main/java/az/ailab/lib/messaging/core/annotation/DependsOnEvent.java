package az.ailab.lib.messaging.core.annotation;

import az.ailab.lib.messaging.infra.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field inside an event payload as a dependency on the successful processing of another event handler.
 *
 * <p>Used in conjunction with {@link EventExecutionMode#DEFERRED}
 * to express execution ordering between event handlers at the aggregate level.</p>
 *
 * <h3>Purpose</h3>
 * This annotation declares that the current event's execution depends on another handler having
 * already successfully processed an event that shares a common aggregate identifier.
 *
 * <p>For example, if a <code>UserCreatedEvent</code> contains a <code>roleId</code> field annotated with
 * <code>@DependsOn(name = "identity.role.created")</code>, the replay engine will defer handling until
 * the <code>identity.role.created</code> handler has successfully processed an event with that role ID.</p>
 *
 * <h3>Annotation Scope</h3>
 * Apply this annotation directly to a field inside the event class — typically fields like foreign keys,
 * reference IDs, or linked aggregates.</p>
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * public record UserCreatedEvent(
 *
 *     @AggregateId
 *     Long id,
 *
 *     @DependsOnEvent(name = "role-events.created", skipIfIn = {"1", "2"})
 *     Long roleId,
 *
 *     @DependsOnEvent(name = "structure-events.created")
 *     Long structureId
 * ) {}
 * }</pre>
 *
 * <h3>Field Parameters</h3>
 * <ul>
 *   <li><b>name</b> – The fully qualified handler name this field depends on, in format: {@code exchange.routingKey}.</li>
 *   <li><b>skipIfIn</b> – Optional set of values (as strings) for which this dependency is ignored (e.g., static, test, or liquibase values).</li>
 *   <li><b>optional</b> – If true, failure to resolve the dependency does not block processing. Used for soft dependencies.</li>
 * </ul>
 *
 * <h3>Resolution Process</h3>
 * <ol>
 *   <li>The field's value is extracted during replay.</li>
 *   <li>The system looks up whether the referenced handler (via <code>name</code>) has completed for that aggregate ID.</li>
 *   <li>If so, this dependency is marked satisfied. If not, the current event remains in PENDING state.</li>
 * </ol>
 *
 * <h3>Note:</h3>
 * This annotation only takes effect when the enclosing handler is operating in {@code DEFERRED} mode.
 * In {@code REAL_TIME}, it is ignored.
 *
 * @see RabbitEventHandler
 * @since 1.3
 * @author tahmazovfarid
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DependsOnEvent {

    /**
     * The fully qualified name of the event handler this field depends on.
     *
     * <p>This must match the {@code exchange.routingKey} of another {@link RabbitEventHandler}
     * which is expected to have successfully processed an event using the same key as this field's value.</p>
     *
     * <p>For example, for a field {@code roleId}, this might be {@code "user-events.role.created"}.</p>
     *
     * <p>This name is used by the replay engine to determine whether the prerequisite event
     * has completed for the specific aggregate (e.g., roleId=123).</p>
     *
     * @return the qualified handler name this field depends on (e.g., "exchange.routingKey")
     */
    String name();

    /**
     * A list of values (as strings) for which this dependency should be skipped.
     *
     * <p>Commonly used for system-reserved values, test data, or bootstrap aggregates
     * where enforcing the dependency check is not necessary.</p>
     *
     * <p>For example, if a {@code structureId} is one of {"1", "2"}, the system will
     * ignore dependency tracking for those values.</p>
     *
     * @return an array of aggregate ID values that should bypass this dependency
     */
    String[] skipIfIn() default {};

    /**
     * Whether this dependency is optional.
     *
     * <p>When set to true, the event will still be processed even if this dependency is
     * unresolved or missing.</p>
     *
     * <p>Useful for soft-coupled flows or backward compatibility scenarios.</p>
     *
     * @return true if dependency failure should be ignored; false to enforce blocking
     */
    boolean optional() default false;

}