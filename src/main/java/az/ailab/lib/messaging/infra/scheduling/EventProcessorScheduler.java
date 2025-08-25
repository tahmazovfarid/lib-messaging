package az.ailab.lib.messaging.infra.scheduling;

import az.ailab.lib.messaging.application.scheduling.EventProcessorSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EventProcessorScheduler {

    private final EventProcessorSchedulingService service;

    @Scheduled(fixedDelay = "#{messageSchedulingProperties.processing.fixedDelayMs}")
    public void processReadyEvents() {
        service.processReadyEvents();
    }

}