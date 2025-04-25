package az.ailab.lib.messaging.outbox.model.entity;

import az.ailab.lib.messaging.outbox.model.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base implementation of the OutboxEventDetails interface.
 * This class provides common functionality for outbox events
 * and is designed to be extended by specific implementations.
 *
 * @author tahmazovfarid
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseOutboxEvent implements OutboxEventDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Column
    private Integer retryCount;

    @Column
    private String errorMessage;

    @Column(nullable = false)
    private UUID traceId;

    /**
     * Default constructor with initialization of default values
     */
    public BaseOutboxEvent(String aggregateId, String eventType, String payload) {
        this(aggregateId, eventType, payload, UUID.randomUUID());
    }

    /**
     * Default constructor with initialization of default values
     */
    public BaseOutboxEvent(String aggregateId, String eventType, String payload, UUID traceId) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
        this.traceId = traceId;
    }

    @Override
    public void markAsProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    @Override
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount = (this.retryCount == null) ? 1 : this.retryCount + 1;
    }

}