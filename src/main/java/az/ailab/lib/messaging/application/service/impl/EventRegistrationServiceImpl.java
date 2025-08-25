package az.ailab.lib.messaging.application.service.impl;

import az.ailab.lib.messaging.application.service.EventRegistrationService;
import az.ailab.lib.messaging.core.domain.Event;
import az.ailab.lib.messaging.core.domain.EventDependency;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.infra.dto.EventMessage;
import az.ailab.lib.messaging.infra.extractor.EventMetaDataExtractor;
import az.ailab.lib.messaging.infra.persistence.repository.EventRepository;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventRegistrationServiceImpl implements EventRegistrationService {

    private final EventMetaDataExtractor extractor;
    private final EventSerializer serializer;
    private final EventRepository eventRepository;

    @Override
    public void register(@NonNull EventProcessingContext context, byte[] serializedEventMessage) {

        EventMessage<?> eventMessage = serializer.deserialize(serializedEventMessage, EventMessage.class);

        // Dependencies extraction
        String aggregateId = extractor.extractAggregateId(eventMessage.getPayload());

        Event event = Event.builder()
                .id(eventMessage.getId())
                .name(context.name())
                .type(eventMessage.getPayload().getClass().getSimpleName())
                .payloadData(serializedEventMessage)
                .maxAttempt(context.retryable().maxAttempts())
                .initDelayMs(convertToMs(context.retryable().initDelay(),
                        context.retryable().timeUnit()))
                .fixedDelayMs(convertToMs(context.retryable().fixedDelay(),
                        context.retryable().timeUnit()))
                .aggregateId(aggregateId)
                .build();

        Set<EventDependency> dependencies = extractor.extractDependencies(eventMessage.getPayload());

        if (areDependenciesReady(dependencies)) {
            event.scheduleForExecution();
        } else {
            event.markAsPending();
        }

        eventRepository.saveWithDependencies(event, dependencies);
    }

    private boolean areDependenciesReady(Set<EventDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return true;
        }
        Set<String> dependencyIds = dependencies.stream()
                .filter(EventDependency::shouldProcess)
                .map(EventDependency::getId)
                .collect(Collectors.toSet());

        return eventRepository.areDependenciesCompleted(dependencyIds);
    }

    private long convertToMs(Long delay, TimeUnit timeUnit) {
        return delay == null ? 0 : timeUnit.toMillis(delay);
    }

}

