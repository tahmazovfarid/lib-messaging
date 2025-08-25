package az.ailab.lib.messaging.infra.persistence.repository;

import az.ailab.lib.messaging.core.domain.Event;
import az.ailab.lib.messaging.core.domain.EventDependency;
import az.ailab.lib.messaging.core.enums.EventStatus;
import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EventRepository {

    void save(Event event);

    void saveWithDependencies(Event event, Set<EventDependency> dependencies);

    Optional<Event> findById(String eventId);

    List<EventEntity> findReadyEvents(LocalDateTime now);

    long countCompletedAggregatesByDependenciesIds(Set<String> dependencyIds);

    List<Event> findPendingEvents();

    List<Event> findEventsReadyForProcessing();

    List<Event> findCompletedEventsBefore(LocalDateTime cutoff);

    List<Event> findFailedEventsEligibleForRetry();

    boolean areDependenciesCompleted(Set<String> dependencyIds);

    void deleteAll(List<Event> events);

    long countPendingEventsByAggregateId(String aggregateId);

    List<Event> findEventsByAggregateIdAndStatus(String aggregateId, EventStatus status);

}
