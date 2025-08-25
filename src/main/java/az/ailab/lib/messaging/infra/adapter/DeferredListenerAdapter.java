package az.ailab.lib.messaging.infra.adapter;

import az.ailab.lib.messaging.application.service.EventRegistrationService;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

@Slf4j
public class DeferredListenerAdapter extends AbstractContainerListenerAdapter {

    private final EventRegistrationService eventRegistrationService;

    public DeferredListenerAdapter(EventProcessingContext eventProcessingContext,
                                   IdempotencyService idempotencyService, EventRegistrationService eventRegistrationService) {
        super(eventProcessingContext, idempotencyService);
        this.eventRegistrationService = eventRegistrationService;
    }

    @Override
    protected void handleMessage(Message message) throws Throwable {
        eventRegistrationService.register(eventProcessingContext, message.getBody());
    }

}
