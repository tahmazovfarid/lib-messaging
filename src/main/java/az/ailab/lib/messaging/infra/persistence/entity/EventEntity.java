package az.ailab.lib.messaging.infra.persistence.entity;

import az.ailab.lib.messaging.infra.persistence.entity.audit.BaseEntity;
import az.ailab.lib.messaging.core.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "events",
        indexes = {
                @Index(name = "idx_events_key", columnList = "key"),
                @Index(name = "idx_events_name", columnList = "name"),
                @Index(name = "idx_events_status", columnList = "status"),
                @Index(name = "idx_events_aggregate_id", columnList = "aggregate_id"),
                @Index(name = "idx_events_created_at", columnList = "created_at")
        }
)
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventEntity extends BaseEntity {

    @Id
    private String id;

    @Column(name = "name")
    private String name;

    private String type;

    @Column(name = "aggregate_id")
    private String aggregateId;

    @Lob
    @Column(name = "payload_data", columnDefinition = "BYTEA")
    private byte[] payloadData;

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY)
    private Set<EventDependencyEntity> dependencies;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @Setter
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    @Column(name = "retry_count")
    @Setter
    @Builder.Default
    private Long retryCount = 0L;

    @Column(name = "max_attempt")
    private Long maxAttempt;

    @Column(name = "next_execution_at")
    @Setter
    private LocalDateTime nextExecutionAt;

    @Column(name = "init_delay_ms")
    private Long initDelayMs;

    @Column(name = "fixed_delay_ms")
    private Long fixedDelayMs;

    @Column(name = "error_message", length = 1000)
    @Setter
    private String errorMessage;

    @Column(name = "error_origin")
    @Setter
    private String errorOrigin;

    @Version
    @Column(name = "version")
    private Long version;

}