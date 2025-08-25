package az.ailab.lib.messaging.infra.strategy;

import az.ailab.lib.messaging.application.service.EventRegistrationService;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.infra.adapter.AbstractContainerListenerAdapter;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface ListenerStrategy {

    AbstractContainerListenerAdapter createAdapter(EventProcessingContext ctx,
                                                   IdempotencyService idem,
                                                   ObjectMapper mapper,
                                                   EventRegistrationService registry);

    EventExecutionMode getSupportedMode();

}