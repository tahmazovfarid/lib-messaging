package az.ailab.lib.messaging.infra.persistence.repository.impl;

import az.ailab.lib.messaging.core.domain.Event;
import az.ailab.lib.messaging.core.domain.EventDependency;
import az.ailab.lib.messaging.core.enums.EventStatus;
import az.ailab.lib.messaging.infra.persistence.entity.EventDependencyEntity;
import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import az.ailab.lib.messaging.infra.persistence.jpa.EventDependencyJpaRepository;
import az.ailab.lib.messaging.infra.persistence.jpa.EventJpaRepository;
import az.ailab.lib.messaging.infra.persistence.mapper.EventMapper;
import az.ailab.lib.messaging.infra.persistence.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepository {

    private final EventJpaRepository eventJpaRepository;
    private final EventDependencyJpaRepository eventDependencyJpaRepository;
    private final EventMapper eventMapper;

    @Override
    public void save(Event event) {
        eventJpaRepository.save(eventMapper.map(event));
    }

    @Override
    @Transactional
    public void saveWithDependencies(Event event, Set<EventDependency> dependencies) {
        save(event);

        if (!dependencies.isEmpty()) {
            Set<EventDependencyEntity> dependencyEntities = dependencies.stream()
                    .map(dependency -> eventMapper.map(dependency, event))
                    .collect(Collectors.toSet());
            eventDependencyJpaRepository.saveAll(dependencyEntities);
        }
    }

    @Override
    public Optional<Event> findById(String eventId) {
        return eventJpaRepository.findById(eventId).map(eventMapper::map);
    }

    @Override
    public List<EventEntity> findReadyEvents(LocalDateTime now) {
        return List.of();
    }

    @Override
    public long countCompletedAggregatesByDependenciesIds(Set<String> dependencyIds) {
        return eventJpaRepository.countCompletedDependencies(dependencyIds);
    }

    @Override
    public List<Event> findPendingEvents() {
        return List.of();
    }

    @Override
    public List<Event> findEventsReadyForProcessing() {
        return List.of();
    }

    @Override
    public List<Event> findCompletedEventsBefore(LocalDateTime cutoff) {
        return List.of();
    }

    @Override
    public List<Event> findFailedEventsEligibleForRetry() {
        return List.of();
    }

    @Override
    public boolean areDependenciesCompleted(Set<String> dependencyIds) {
        if (dependencyIds.isEmpty()) {
            return true;
        }

        long completedCount = countCompletedAggregatesByDependenciesIds(dependencyIds);
        return completedCount == dependencyIds.size();
    }

    @Override
    public void deleteAll(List<Event> events) {

    }

    @Override
    public long countPendingEventsByAggregateId(String aggregateId) {
        return 0;
    }

    @Override
    public List<Event> findEventsByAggregateIdAndStatus(String aggregateId, EventStatus status) {
        return List.of();
    }

}
