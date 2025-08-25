package az.ailab.lib.messaging.application.service;

import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import lombok.NonNull;

public interface EventRegistrationService {

    void register(@NonNull EventProcessingContext context, byte[] serializedEventMessage);

}
