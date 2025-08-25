package az.ailab.lib.messaging.core.vo;

import az.ailab.lib.messaging.infra.annotation.Idempotent;
import az.ailab.lib.messaging.infra.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.infra.annotation.RabbitEventListener;
import az.ailab.lib.messaging.infra.annotation.Retryable;
import az.ailab.lib.messaging.core.enums.EventExecutionMode;
import az.ailab.lib.messaging.core.enums.ExchangeType;
import java.lang.reflect.Method;
import lombok.Builder;
import lombok.NonNull;

@Builder
public record EventProcessingContext(@NonNull Object bean,
                                     @NonNull Method method,
                                     @NonNull RabbitEventListener listenerConfig,
                                     @NonNull RabbitEventHandler handlerConfig,
                                     @NonNull String queue) {

    public String name() {
        return exchange() + "." + routingKey();
    }

    public String exchange() {
        return listenerConfig.exchange();
    }

    public String routingKey() {
        return listenerConfig.exchange();
    }

    public ExchangeType exchangeType() {
        return listenerConfig.exchangeType();
    }

    public Retryable retryable() {
        return handlerConfig.retryable();
    }

    public boolean isRetryableEnabled() {
        return retryable() != null && retryable().enabled();
    }

    public String dlxName() {
        return exchange() + listenerConfig().dlxSuffix();
    }

    public String dlqName() {
        return queue + handlerConfig.dlqSuffix();
    }

    public boolean isAutoCreateEnabled() {
        return listenerConfig.autoCreate();
    }

    public String retryXSuffix() {
        return exchange() + retryable().retryXSuffix();
    }

    public String retryQSuffix() {
        return queue + retryable().retryQSuffix();
    }

    public EventExecutionMode mode() {
        return handlerConfig.mode();
    }

    public int minConsumers() {
        return handlerConfig().minConsumers();
    }

    public int maxConsumers() {
        int maxConsumers = handlerConfig().maxConsumers();
        return maxConsumers > 0 ? maxConsumers : minConsumers();
    }

    public int prefetchCount() {
        return handlerConfig.prefetch();
    }

    public Idempotent idempotent() {
        return handlerConfig().idempotent();
    }

    public boolean isIdempotencyEnabled() {
        return idempotent().enabled();
    }

}
