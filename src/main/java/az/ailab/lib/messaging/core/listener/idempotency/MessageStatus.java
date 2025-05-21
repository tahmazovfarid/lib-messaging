package az.ailab.lib.messaging.core.listener.idempotency;

/**
 * Represents the processing status of a message in an idempotent message-handling system.
 * <p>
 * This enum is used to track whether a message has already been processed,
 * is currently being processed, or has previously failed.
 *
 * @since 1.2.1
 * @author tahmazovfarid
 */
public enum MessageStatus {

    /**
     * Message has been seen for the first time or is currently being processed.
     */
    PENDING,

    /**
     * Message has already been successfully processed and should not be processed again.
     */
    PROCESSED,

    /**
     * Message processing failed. Depending on the system logic, it may be retried later.
     */
    FAILED;

    /**
     * Parses a string into a {@link MessageStatus} enum value.
     *
     * @param raw the raw string value (case-sensitive)
     * @return the corresponding {@code MessageStatus}
     * @throws IllegalArgumentException if the value is not a valid enum name
     */
    public static MessageStatus from(String raw) {
        return MessageStatus.valueOf(raw);
    }

}