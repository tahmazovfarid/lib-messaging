package az.ailab.lib.messaging.core.domain;

import az.ailab.lib.messaging.core.enums.EventStatus;
import az.ailab.lib.messaging.infra.util.StackTraceUtil;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Event {

    @EqualsAndHashCode.Include
    private String id;

    private String name;

    private String type;

    private String aggregateId;

    private byte[] payloadData;

    private Set<EventDependency> dependencies;

    @Setter
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    @Builder.Default
    private Long retryCount = 0L;

    private Long maxAttempt;

    private LocalDateTime nextExecutionAt;

    private Long initDelayMs;

    @NotNull
    private Long fixedDelayMs;

    private String errorMessage;

    private String errorOrigin;

    public void scheduleForExecution() {
        Long delayMs = retryCount == 0 ? initDelayMs : fixedDelayMs;

        this.nextExecutionAt = delayMs == null ?
                LocalDateTime.now() :
                LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);

        setStatus(EventStatus.READY);
    }

    public void scheduleForRetry(Exception exception, String listenerPackage) {
        if (!canRetry()) {
            markAsFailed(exception, listenerPackage);
            return;
        }
        markAsFailedAttempt(exception, listenerPackage);
        scheduleForExecution();
    }

    public void markAsProcessing() {
        this.retryCount++;
        this.status = EventStatus.PROCESSING;
    }

    public void markAsCompleted() {
        this.status = EventStatus.COMPLETED;
    }

    public void markAsPending() {
        this.status = EventStatus.PENDING;
    }

    public boolean canRetry() {
        return maxAttempt == null || maxAttempt == -1 || retryCount < maxAttempt;
    }

    private boolean canTransitionTo(EventStatus newStatus) {
        return switch (status) {
            case PENDING -> newStatus == EventStatus.READY;
            case READY -> newStatus == EventStatus.PROCESSING;
            case PROCESSING -> Set.of(EventStatus.COMPLETED, EventStatus.READY).contains(newStatus);
            case COMPLETED, FAILED -> false; // Terminal state
        };
    }

    private void markAsFailed(Exception exception, String listenerPackage) {
        this.status = EventStatus.FAILED;
        markAsFailedAttempt(exception, listenerPackage);
    }

    private void markAsFailedAttempt(Exception exception, String listenerPackage) {
        StringBuilder builder = new StringBuilder(exception.getMessage());
        if (exception.getCause() != null) {
            builder
                    .append(" cause ")
                    .append(exception.getCause().getMessage());
        }
        this.errorMessage = builder.toString();
        this.errorOrigin = StackTraceUtil.extractOriginException(
                exception.getStackTrace(), listenerPackage);
    }

}
