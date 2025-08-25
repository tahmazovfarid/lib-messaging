package az.ailab.lib.messaging.infra.scheduling;

import az.ailab.lib.messaging.application.scheduling.EventCleanUpSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventCleanupScheduler {
    
    private final EventCleanUpSchedulingService service;

    /**
     * Periodically clean up completed events according to cron and threshold.
     */
    @Scheduled(cron = "#{messageSchedulingProperties.cleanup.cron}")
    public void cleanupCompletedEvents() {
        int countCleanedEvents = service.cleanUpCompletedEvents();
        log.info("Cleaned up {} completed events older than 7 days", countCleanedEvents);
    }

}