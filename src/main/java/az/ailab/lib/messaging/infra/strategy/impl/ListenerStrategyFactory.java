package az.ailab.lib.messaging.infra.strategy.impl;

import az.ailab.lib.messaging.application.service.EventRegistrationService;
import az.ailab.lib.messaging.application.service.IdempotencyService;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.infra.adapter.AbstractContainerListenerAdapter;
import az.ailab.lib.messaging.infra.strategy.ListenerStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ListenerStrategyFactory {

    private final Map<EventExecutionMode, ListenerStrategy> strategies;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final EventRegistrationService eventRegistrationService;

    public ListenerStrategyFactory(List<ListenerStrategy> strategyList,
                                   ObjectMapper objectMapper,
                                   IdempotencyService idempotencyService,
                                   EventRegistrationService registrationService) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(ListenerStrategy::getSupportedMode, Function.identity()));
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.eventRegistrationService = registrationService;
    }

    public AbstractContainerListenerAdapter createAdapter(EventProcessingContext ctx) {
        ListenerStrategy strategy = strategies.get(ctx.mode());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported execution mode: " + ctx.mode());
        }
        return strategy.createAdapter(ctx, idempotencyService, objectMapper, eventRegistrationService);
    }

}