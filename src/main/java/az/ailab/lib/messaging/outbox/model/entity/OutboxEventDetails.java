package az.ailab.lib.messaging.outbox.model.entity;

import az.ailab.lib.messaging.outbox.model.enums.OutboxStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Interface describing outbox events.
 * This interface allows working with outbox events regardless of the specific implementation.
 *
 * @author tahmazovfarid
 */
public interface OutboxEventDetails {

    /**
     * Gets the unique identifier of the event
     */
    Long getId();

    /**
     * Gets the aggregate ID - the identifier of the domain object this event belongs to
     */
    String getAggregateId();

    /**
     * Gets the event type (e.g., USER_PROFILE, ORDER_CREATED)
     */
    String getEventType();

    /**
     * Gets the event payload (in JSON format)
     */
    String getPayload();

    /**
     * Gets the current status of the event
     */
    OutboxStatus getStatus();

    /**
     * Gets the timestamp when the event was created
     */
    LocalDateTime getCreatedAt();

    /**
     * Gets the timestamp when the event was processed
     */
    LocalDateTime getProcessedAt();

    /**
     * Gets the retry count (incremented when processing fails)
     */
    Integer getRetryCount();

    /**
     * Gets the error message (when processing fails)
     */
    String getErrorMessage();

    /**
     * Gets the trace ID (for tracing and logging purposes)
     */
    UUID getTraceId();

    /**
     * Marks the event as processed
     */
    void markAsProcessed();

    /**
     * Marks the event as failed with an error message
     *
     * @param errorMessage The error message describing the failure
     */
    void markAsFailed(String errorMessage);

}