package az.ailab.lib.messaging.outbox.repository;

import az.ailab.lib.messaging.outbox.model.entity.OutboxEventDetails;
import az.ailab.lib.messaging.outbox.model.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.NoRepositoryBean;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Generic repository interface for outbox events.
 * Designed to be extended by specific implementations.
 *
 * @param <T> The specific outbox event implementation
 * @author tahmazovfarid
 */
@NoRepositoryBean
public interface OutboxRepository<T extends OutboxEventDetails> extends JpaRepository<T, Long> {
    
    /**
     * Finds events by their status
     *
     * @param status The status to filter by
     * @return List of events with the specified status
     */
    @Query("SELECT o FROM #{#entityName} o WHERE o.status = :status ORDER BY o.createdAt ASC")
    List<T> findByStatus(@Param("status") OutboxStatus status);
    
    /**
     * Finds events by status that were created before a specific time
     *
     * @param status The status to filter by
     * @param before The cutoff timestamp
     * @return List of events matching the criteria
     */
    @Query("SELECT o FROM #{#entityName} o WHERE o.status = :status AND o.createdAt <= :before ORDER BY o.createdAt ASC")
    List<T> findByStatusAndCreatedBefore(
            @Param("status") OutboxStatus status,
            @Param("before") LocalDateTime before);
    
    /**
     * Finds failed events that are eligible for retry
     *
     * @param status The status (usually FAILED)
     * @param maxRetries The maximum number of retries allowed
     * @return List of events eligible for retry
     */
    @Query("SELECT o FROM #{#entityName} o WHERE o.status = :status AND (o.retryCount IS NULL OR o.retryCount < :maxRetries) ORDER BY o.createdAt ASC")
    List<T> findFailedForRetry(
            @Param("status") OutboxStatus status,
            @Param("maxRetries") Integer maxRetries);
}