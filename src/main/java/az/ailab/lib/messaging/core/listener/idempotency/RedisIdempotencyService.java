package az.ailab.lib.messaging.core.listener.idempotency;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * Redis-backed implementation of {@link IdempotencyService} for ensuring
 * exactly-once processing semantics in distributed message handling systems.
 * <p>
 * This service uses Redis to store message IDs along with their processing status
 * (PENDING, PROCESSED, FAILED). It ensures that duplicate messages are not processed
 * more than once, even across multiple application instances or threads.
 * <p>
 * TTL (time-to-live) is applied per message to avoid memory leaks and to allow
 * message retries if processing fails.
 *
 * @since 1.2.1
 * @author tahmazovfarid
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedisIdempotencyService implements IdempotencyService {

    private final RedissonClient redissonClient;

    /**
     * Checks whether the message with the given ID should be processed.
     * <p>
     * If the message ID does not exist in Redis or its TTL has expired, it will be registered
     * with a {@code PENDING} status and an expiration time based on the given TTL.
     * <p>
     * If the message is already marked as {@code PROCESSED}, this method returns {@code false}.
     *
     * @param messageId the unique identifier of the incoming message
     * @param ttlMs     time-to-live in milliseconds for how long to retain the idempotency record
     * @return {@code true} if the message should be processed; {@code false} otherwise
     */
    @Override
    public boolean shouldProcess(String messageId, String queueName, long ttlMs) {
        String redisKey = buildRedisKey(messageId, queueName);
        log.debug("[Idempotency] Checking if message should be processed | key={} | ttlMs={}", redisKey, ttlMs);
        RMap<String, String> map = redissonClient.getMap(redisKey);

        // Try to set status atomically if absent
        String existing = map.putIfAbsent("status", MessageStatus.PENDING.name());
        if (existing == null) {
            map.expire(Duration.ofMillis(ttlMs));
            log.debug("[Idempotency] Message registered as PENDING | key={} | expiresIn={}ms", redisKey, ttlMs);
            return true; // First processor
        }

        // Check if status is non-terminal
        if (MessageStatus.PROCESSED.name().equals(existing)) {
            log.debug("[Idempotency] Duplicate message detected, skipping | key={} | status={}", redisKey, existing);
            return false;
        }

        log.debug("[Idempotency] Message already exists but not PROCESSED | key={} | status={}", redisKey, existing);
        return true;
    }

    /**
     * Marks the message with the given ID as successfully processed.
     * This prevents future attempts to process the same message again.
     *
     * @param messageId the unique identifier of the message
     */
    @Override
    public void markProcessed(String messageId, String queueName) {
        String redisKey = buildRedisKey(messageId, queueName);
        log.debug("[Idempotency] Marking message as PROCESSED | key={}", redisKey);

        RMap<String, String> map = redissonClient.getMap(redisKey);
        map.put("status", MessageStatus.PROCESSED.name());
        log.info("[Idempotency] Message marked as PROCESSED | key={}", redisKey);
    }

    /**
     * Marks the message with the given ID as failed.
     * This typically allows the message to be retried depending on TTL configuration.
     *
     * @param messageId the unique identifier of the message
     */
    @Override
    public void markFailed(String messageId, String queueName) {
        String redisKey = buildRedisKey(messageId, queueName);
        log.debug("[Idempotency] Marking message as FAILED | key={}", redisKey);

        RMap<String, String> map = redissonClient.getMap(redisKey);
        map.put("status", MessageStatus.FAILED.name());
        log.info("[Idempotency] Message marked as FAILED | key={}", redisKey);
    }

    private String buildRedisKey(String messageId, String queueName) {
        return "idempotency:" + queueName + ":" + messageId;
    }

}