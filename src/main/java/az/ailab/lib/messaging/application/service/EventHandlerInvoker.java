package az.ailab.lib.messaging.application.service;

import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import org.springframework.amqp.core.Message;

public interface EventHandlerInvoker {

    void invoke(EventProcessingContext context, Message message);

}