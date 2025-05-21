package az.ailab.lib.messaging.core.listener.idempotency;

/**
 * Defines the contract for idempotency handling in message-driven systems.
 * <p>
 * Implementations of this interface are responsible for ensuring that a message with
 * a given ID is processed exactly once, even in the presence of retries, duplicates,
 * or parallel message consumption.
 *
 * @since 1.2.1
 * @author tahmazovfarid
 */
public interface IdempotencyService {

    /**
     * Determines whether the message with the given ID should be processed.
     * <p>
     * This method must be atomic and concurrency-safe to prevent race conditions.
     *
     * @param messageId the unique identifier of the message
     * @param ttlMs     time-to-live in milliseconds for retaining idempotency metadata
     * @return {@code true} if the message has not been processed and should be handled;
     *         {@code false} if it has already been processed
     */
    boolean shouldProcess(String messageId, long ttlMs);

    /**
     * Marks the message as successfully processed. After this call,
     * the message should no longer be eligible for re-processing.
     *
     * @param messageId the unique identifier of the message
     */
    void markProcessed(String messageId);

    /**
     * Marks the message as failed. This may allow it to be retried depending
     * on the implementation and its expiration policy.
     *
     * @param messageId the unique identifier of the message
     */
    void markFailed(String messageId);

}
