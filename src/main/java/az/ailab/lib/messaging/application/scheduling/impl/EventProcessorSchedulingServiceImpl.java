package az.ailab.lib.messaging.application.scheduling.impl;

import az.ailab.lib.messaging.core.domain.Event;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.infra.dto.EventMessage;
import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import az.ailab.lib.messaging.infra.persistence.mapper.EventMapper;
import az.ailab.lib.messaging.infra.persistence.jpa.EventJpaRepository;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import az.ailab.lib.messaging.shared.context.EventContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventProcessorSchedulingServiceImpl {

    private final EventJpaRepository repository;
    private final EventMapper eventMapper;
    private final EventSerializer serializer;
    private final EventContextHolder cachedEventMetadataContextHolder;

    @Scheduled(fixedDelay = 15000)
    public void processReadyEvents() {

        List<EventEntity> readyEventEntities = repository.findReadyEvents(LocalDateTime.now());

        for (EventEntity eventEntity : readyEventEntities) {
            processEvent(eventMapper.map(eventEntity));
        }
    }

    private void processEvent(Event event) {
        try {
            event.markAsProcessing();
            repository.save(eventMapper.map(event));

            EventMessage<?> payload = serializer.deserialize(event.getPayloadData(), EventMessage.class);

            executeHandler(event.getName(), payload);

            event.markAsCompleted();
            repository.save(eventMapper.map(event));

            log.info("Event {} completed after {} attempts", event.getId(), event.getRetryCount());
        } catch (Exception e) {
            event.scheduleForRetry(e, pckg);

            log.warn("Event {} failed (attempt {}), retrying in {}ms: {}",
                    event.getId(),
                    event.getRetryCount(),
                    event.getFixedDelayMs(),
                    e.getMessage());
        }
    }

    private void executeHandler(String listenerName, EventMessage<?> payload) {
        Optional<EventProcessingContext> cachedMetadata = cachedEventMetadataContextHolder.getCachedMetadata(listenerName);
    }

}
