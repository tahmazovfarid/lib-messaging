package az.ailab.lib.messaging.infra.strategy.impl;

import az.ailab.lib.messaging.application.service.EventRegistrationService;
import az.ailab.lib.messaging.infra.adapter.AbstractContainerListenerAdapter;
import az.ailab.lib.messaging.infra.adapter.DeferredListenerAdapter;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import az.ailab.lib.messaging.infra.strategy.ListenerStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DeferredListenerStrategy implements ListenerStrategy {

    @Override
    public AbstractContainerListenerAdapter createAdapter(EventProcessingContext ctx, IdempotencyService idem,
                                                          ObjectMapper mapper, EventRegistrationService registry) {
        return new DeferredListenerAdapter(ctx, idem, registry);
    }

    @Override
    public EventExecutionMode getSupportedMode() {
        return EventExecutionMode.DEFERRED;
    }

}