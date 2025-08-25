package az.ailab.lib.messaging.core.enums;

/**
 * Represents the processing lifecycle status of an event within the outbox or event store mechanism.
 *
 * <p>This enum is used to track and control the state of event delivery and replay across
 * the system. It enables idempotent handling, retry coordination, and consistency guarantees.</p>
 *
 * <p>Status transitions may vary slightly depending on execution mode (REAL_TIME vs DEFERRED),
 * but the core semantics are consistent.</p>
 *
 * <h2>Status Semantics</h2>
 * <ul>
 *   <li><b>{@code PENDING}</b> – Event is newly received or persisted but has not yet been scheduled for processing.</li>
 *   <li><b>{@code READY}</b> – Event is eligible and ready for processing, typically after dependency checks (e.g., all prerequisites are met).</li>
 *   <li><b>{@code PROCESSING}</b> – Event is currently being handled by a worker or thread. Should be used to avoid duplicate parallel executions.</li>
 *   <li><b>{@code COMPLETED}</b> – Event has been successfully handled and requires no further action. Final terminal state.</li>
 *   <li><b>{@code FAILED}</b> – Event processing encountered an unrecoverable error or max retry attempts have been exhausted. Can be manually replayed or ignored based on use case.</li>
 * </ul>
 *
 * @author tahmazovfarid
 */
public enum EventStatus {

    /** Event is newly saved but not yet scheduled or evaluated. */
    PENDING,

    /** Event has passed dependency checks and is ready to be dispatched. */
    READY,

    /** Event is currently under processing; prevents concurrent handling. */
    PROCESSING,

    /** Event was successfully processed and acknowledged. */
    COMPLETED,

    /** Event processing failed; may be retried, ignored, or escalated. */
    FAILED
}