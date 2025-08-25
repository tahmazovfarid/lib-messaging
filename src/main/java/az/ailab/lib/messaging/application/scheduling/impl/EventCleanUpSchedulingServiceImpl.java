package az.ailab.lib.messaging.application.scheduling.impl;

import az.ailab.lib.messaging.core.enums.EventStatus;
import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import az.ailab.lib.messaging.infra.persistence.jpa.EventJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventCleanUpSchedulingServiceImpl {

    private final EventJpaRepository repository;

    public int cleanUpCompletedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        List<EventEntity> oldCompleted = repository
            .findByStatusAndCreatedAtBefore(EventStatus.COMPLETED, cutoff);

        repository.deleteAll(oldCompleted);

        return oldCompleted.size();
    }

}
