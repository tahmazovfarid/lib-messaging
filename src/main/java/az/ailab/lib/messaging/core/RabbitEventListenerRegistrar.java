package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.RabbitEventListener;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import java.util.Map;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * Registrar that processes {@link RabbitEventListener} annotations.
 *
 * <p>This class scans for classes annotated with {@link RabbitEventListener} and logs
 * information about the exchanges they are listening to.</p>
 *
 * @author tahmazovfarid
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitEventListenerRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ExchangeNameResolver exchangeNameResolver;

    /**
     * This method is called when the Spring ApplicationContext is fully initialized.
     *
     * <p>It scans all Spring-managed beans for the {@link RabbitEventListener} annotation
     * and logs information about the exchanges they are listening to.</p>
     *
     * @param event the event object
     */
    @Override
    public void onApplicationEvent(@NonNull final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        // Get all beans with RabbitEventListener annotation
        Map<String, Object> listeners = ctx.getBeansWithAnnotation(RabbitEventListener.class);

        listeners.values().stream()
                .map(bean -> AnnotationUtils.findAnnotation(bean.getClass(), RabbitEventListener.class))
                .filter(Objects::nonNull)
                .forEach(this::processRabbitEventListenerAnnotation);
    }

    /**
     * Processes an {@link RabbitEventListener} annotation and logs information about the exchange.
     *
     * @param annotation the annotation
     */
    private void processRabbitEventListenerAnnotation(final RabbitEventListener annotation) {
        final String exchangeName = annotation.exchange();
        final String resolvedExchangeName = exchangeNameResolver.resolveExchangeName(exchangeName);

        log.debug("Registered listener for exchange: {}", resolvedExchangeName);
        log.debug("Exchange type specified in annotation: {}", annotation.exchangeType());
    }

}