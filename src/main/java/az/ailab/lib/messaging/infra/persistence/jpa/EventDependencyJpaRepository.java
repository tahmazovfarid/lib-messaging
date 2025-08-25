package az.ailab.lib.messaging.infra.persistence.jpa;

import az.ailab.lib.messaging.infra.persistence.entity.EventDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDependencyJpaRepository extends JpaRepository<EventDependencyEntity, String> {

}
