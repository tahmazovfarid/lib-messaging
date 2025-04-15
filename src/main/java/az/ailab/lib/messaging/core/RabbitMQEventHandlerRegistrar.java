package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.annotation.RabbitEventListener;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.QueueNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import java.lang.reflect.Method;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Registrar that processes {@link RabbitEventHandler} annotations and sets up message listeners.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitMQEventHandlerRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ConnectionFactory connectionFactory;
    private final MessageConverter messageConverter;
    private final ExchangeNameResolver exchangeNameResolver;
    private final QueueNameResolver queueNameResolver;
    private final RoutingKeyResolver routingKeyResolver;

    @Override
    public void onApplicationEvent(@NonNull final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        // Get all beans with RabbitEventListener annotation
        Map<String, Object> listeners = ctx.getBeansWithAnnotation(RabbitEventListener.class);

        listeners.values().forEach(bean -> {
            RabbitEventListener classAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), RabbitEventListener.class);
            if (classAnnotation != null) {
                processEventHandlerAnnotations(bean, classAnnotation);
            }
        });
    }

    private void processEventHandlerAnnotations(final Object bean,
                                                final RabbitEventListener classAnnotation) {
        final String exchangeName = classAnnotation.exchange();
        final String resolvedExchangeName = exchangeNameResolver.resolveExchangeName(exchangeName);

        log.debug("Setting up message listeners for exchange: {}", resolvedExchangeName);

        // Process each method with @RabbitEventHandler annotation
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            RabbitEventHandler methodAnnotation = AnnotationUtils.findAnnotation(method, RabbitEventHandler.class);

            if (methodAnnotation != null) {
                try {
                    processEventHandlerMethod(bean, method, methodAnnotation, resolvedExchangeName);
                } catch (Exception e) {
                    log.error("Failed to process event handler method: {} due to: {}", method.getName(), e.getMessage(), e);
                }
            }
        });
    }

    private void processEventHandlerMethod(final Object bean,
                                           final Method method,
                                           final RabbitEventHandler annotation,
                                           final String exchangeName) {
        final String routingKey = annotation.routingKey();
        final String resolvedRoutingKey = routingKeyResolver.resolveRoutingKey(routingKey);

        // Determine queue name
        String queueName = annotation.queue();
        if (queueName.isEmpty()) {
            queueName = resolvedRoutingKey.equals("#") ? exchangeName // Use exchange name for catch-all
                    : exchangeName + "." + resolvedRoutingKey;
        }

        // Resolve full queue name with prefix
        final String resolvedQueueName = queueNameResolver.resolveQueueName(queueName);
        log.debug("Setting up listener for queue: {} with routing key: {}", resolvedQueueName, resolvedRoutingKey);

        // Create the message listener
        createMessageListener(bean, method, resolvedQueueName, annotation.minConsumers(), annotation.maxConsumers());
    }

    private void createMessageListener(final Object bean,
                                       final Method method,
                                       final String queueName,
                                       final int minConsumers,
                                       final int maxConsumers) {
        try {
            final MessageListenerAdapter listenerAdapter = new MessageListenerAdapter(bean);
            listenerAdapter.setDefaultListenerMethod(method.getName());
            listenerAdapter.setMessageConverter(messageConverter);

            final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(queueName);
            container.setMessageListener(listenerAdapter);

            // Set up error handling
            container.setErrorHandler(t -> {
                log.error("Error in message listener for queue {}: {}", queueName, t.getMessage(), t);
            });

            // Configure to not fail if queue doesn't exist yet
            container.setMissingQueuesFatal(false);

            // Configure concurrency if specified
            if (minConsumers > 0) {
                container.setConcurrentConsumers(minConsumers);

                final int effectiveMaxConsumers = maxConsumers > 0 ? maxConsumers : minConsumers;
                container.setMaxConcurrentConsumers(effectiveMaxConsumers);

                log.debug("Configured concurrency for queue: {} with min: {} and max: {}",
                        queueName, minConsumers, effectiveMaxConsumers);
            }

            container.start();
            log.debug("Started message listener for queue: {}", queueName);
        } catch (Exception e) {
            log.error("Failed to create message listener for queue: {}", queueName, e);
        }
    }

}