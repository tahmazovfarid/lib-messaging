package az.ailab.lib.messaging.outbox.core;

import az.ailab.lib.messaging.outbox.model.entity.OutboxEventDetails;
import az.ailab.lib.messaging.outbox.model.enums.OutboxStatus;
import az.ailab.lib.messaging.outbox.model.vo.OutboxEventVo;
import az.ailab.lib.messaging.outbox.properties.OutboxProperties;
import az.ailab.lib.messaging.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract implementation of the Outbox Pattern service.
 * This class provides the core functionality for handling outbox events,
 * while allowing specific implementations to define how events are created and stored.
 *
 * @param <T> The specific outbox event implementation
 * @author tahmazovfarid
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractOutboxService<T extends OutboxEventDetails> {

    private final ObjectMapper objectMapper;
    private final Set<EventProcessor> eventProcessors;
    private final OutboxProperties outboxProperties;
    
    /**
     * Get domain name for configuration purposes
     * @return domain name
     */
    protected abstract String getDomainName();

    /**
     * Get the repository for storing and retrieving outbox events
     *
     * @return The outbox repository
     */
    protected abstract OutboxRepository<T> getOutboxRepository();

    /**
     * Create a new outbox event instance
     *
     * @param vo The VO containing the event details
     * @return A new outbox event
     */
    protected abstract T createOutboxEvent(OutboxEventVo vo);

    /**
     * Saves an event to the outbox table.
     * This method should be called within a business transaction.
     *
     * @param aggregateId The identifier of the domain object
     * @param eventType The type of event
     * @param payload The event payload (will be converted to JSON)
     * @return The created outbox event
     */
    @Transactional
    public T saveEvent(String aggregateId, String eventType, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            return saveEventJson(aggregateId, eventType, jsonPayload);
        } catch (Exception e) {
            log.error("Failed to serialize payload for outbox event", e);
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    /**
     * Saves an event with an already serialized JSON payload.
     *
     * @param aggregateId The identifier of the domain object
     * @param eventType The type of event
     * @param jsonPayload The event payload in JSON format
     * @return The created outbox event
     */
    @Transactional
    public T saveEventJson(String aggregateId, String eventType, String jsonPayload) {
        OutboxEventVo dto = OutboxEventVo.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(jsonPayload)
                .build();

        T outboxEvent = createOutboxEvent(dto);
        return getOutboxRepository().save(outboxEvent);
    }

    /**
     * Processes pending outbox events.
     * Runs periodically based on the configured interval.
     */
    @Scheduled(fixedDelayString = "#{@outboxProperties.forDomain('${T(java.util.Objects).requireNonNull(T(org.springframework.aop.framework.AopProxyUtils).ultimateTargetClass(this).cast(this)).getDomainName()}').processingInterval}")
    @Transactional
    public void processOutboxEvents() {
        OutboxProperties.OutboxDomainConfig config = outboxProperties.forDomain(getDomainName());
        
        if (!config.isProcessingEnabled()) {
            log.debug("Event processing disabled for domain: {}", getDomainName());
            return;
        }
        
        List<T> pendingEvents = getOutboxRepository().findByStatus(OutboxStatus.PENDING);
        log.debug("[{}] Processing {} pending outbox events", getDomainName(), pendingEvents.size());

        for (T event : pendingEvents) {
            processEvent(event);
        }
    }

    /**
     * Retries failed outbox events.
     * Runs periodically based on the configured interval.
     */
    @Scheduled(fixedDelayString = "#{@outboxProperties.forDomain('${T(java.util.Objects).requireNonNull(T(org.springframework.aop.framework.AopProxyUtils).ultimateTargetClass(this).cast(this)).getDomainName()}').retryInterval}")
    @Transactional
    public void retryFailedEvents() {
        OutboxProperties.OutboxDomainConfig config = outboxProperties.forDomain(getDomainName());
        
        if (!config.isProcessingEnabled()) {
            return;
        }
        
        List<T> failedEvents = getOutboxRepository().findFailedForRetry(
                OutboxStatus.FAILED, 
                config.getMaxRetries());
                
        log.debug("[{}] Retrying {} failed outbox events", getDomainName(), failedEvents.size());

        for (T event : failedEvents) {
            processEvent(event);
        }
    }

    /**
     * Cleans up old processed events.
     * Runs according to the configured cron schedule.
     */
    @Scheduled(cron = "#{@outboxProperties.forDomain('${T(java.util.Objects).requireNonNull(T(org.springframework.aop.framework.AopProxyUtils).ultimateTargetClass(this).cast(this)).getDomainName()}').cleanupCron}")
    @Transactional
    public void cleanupProcessedEvents() {
        OutboxProperties.OutboxDomainConfig config = outboxProperties.forDomain(getDomainName());
        
        if (!config.isProcessingEnabled()) {
            return;
        }
        
        LocalDateTime threshold = LocalDateTime.now().minusDays(config.getRetentionDays());
        List<T> oldEvents = getOutboxRepository().findByStatusAndCreatedBefore(OutboxStatus.PROCESSED, threshold);

        if (!oldEvents.isEmpty()) {
            getOutboxRepository().deleteAll(oldEvents);
            log.info("[{}] Cleaned up {} old processed outbox events", getDomainName(), oldEvents.size());
        }
    }

    /**
     * Processes a single outbox event.
     *
     * @param event The event to process
     */
    protected void processEvent(T event) {
        try {
            // Find the appropriate processor for this event type
            Optional<EventProcessor> processor = eventProcessors.stream()
                    .filter(p -> p.canProcess(event.getEventType()))
                    .findFirst();

            if (processor.isPresent()) {
                processor.get().processEvent(event.getPayload());
                event.markAsProcessed();
                getOutboxRepository().save(event);
                log.debug("Successfully processed outbox event: {}", event.getId());
            } else {
                log.warn("No processor found for event type: {}", event.getEventType());
                // Don't mark as failed - might be implemented later
            }
        } catch (Exception e) {
            log.error("Failed to process outbox event: {}", event.getId(), e);
            event.markAsFailed(e.getMessage());
            getOutboxRepository().save(event);
        }
    }

}