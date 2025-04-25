package az.ailab.lib.messaging.outbox.model.enums;

/**
 * Enum representing the possible statuses of an outbox event.
 *
 * @author tahmazovfarid
 */
public enum OutboxStatus {

    /**
     * Event is pending processing
     */
    PENDING,

    /**
     * Event has been successfully processed
     */
    PROCESSED,

    /**
     * Event processing has failed
     */
    FAILED

}