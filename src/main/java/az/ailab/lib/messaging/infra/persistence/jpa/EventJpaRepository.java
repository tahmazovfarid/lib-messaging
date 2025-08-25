package az.ailab.lib.messaging.infra.persistence.jpa;

import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventJpaRepository extends JpaRepository<EventEntity, String> {

    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.status = 'READY'
            AND e.nextExecutionAt <= :now
            ORDER BY e.createdAt ASC
            """)
    List<EventEntity> findReadyEvents(@Param("now") LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*)
            FROM events e
            WHERE e.status = 'COMPLETED' AND CONCAT(e.name, ':', e.aggregate_id) IN (:dependencyIds)
            """, nativeQuery = true)
    long countCompletedDependencies(@Param("dependencyIds") Set<String> dependencyIds);

}
