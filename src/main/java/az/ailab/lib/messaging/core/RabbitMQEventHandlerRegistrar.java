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
import org.springframework.amqp.core.AmqpAdmin;
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
 * Responsible for registering RabbitMQ listeners annotated with
 * {@link RabbitEventListener} and {@link RabbitEventHandler}.
 * Scans all beans after Spring context is refreshed,
 * sets up exchanges, queues, bindings, and listeners accordingly.
 * @author tahmazovfarid
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
    private final AmqpAdmin amqpAdmin;
    private final RabbitMQInfrastructure infrastructure;

    /**
     * Triggers when Spring context is fully initialized.
     * Scans for @RabbitEventListener beans and processes their handler methods.
     */
    @Override
    public void onApplicationEvent(@NonNull final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        log.debug("Starting RabbitMQ listener registration process");

        // Get all beans with RabbitEventListener annotation
        Map<String, Object> listeners = ctx.getBeansWithAnnotation(RabbitEventListener.class);

        listeners.values().forEach(bean -> {
            RabbitEventListener classAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), RabbitEventListener.class);
            if (classAnnotation != null) {
                log.debug("Processing bean: {} with RabbitEventListener", bean.getClass().getName());
                processEventHandlerAnnotations(bean, classAnnotation);
            }
        });
    }

    private void processEventHandlerAnnotations(final Object bean, final RabbitEventListener classAnnotation) {
        final ExchangeType exchangeType = classAnnotation.exchangeType();
        final String resolvedExchangeName = exchangeNameResolver.resolveExchangeName(classAnnotation.exchange());
        final boolean autoCreate = classAnnotation.autoCreate();

        log.debug("Resolved exchange: {}, type: {}, autoCreate: {}", resolvedExchangeName, exchangeType, autoCreate);

        // Process each method with @RabbitEventHandler annotation
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            RabbitEventHandler methodAnnotation = AnnotationUtils.findAnnotation(method, RabbitEventHandler.class);

            if (methodAnnotation != null) {
                try {
                    processEventHandlerMethod(bean, method, methodAnnotation, resolvedExchangeName, exchangeType, autoCreate);
                } catch (Exception e) {
                    log.error("Failed to process event handler method: {} due to: {}", method.getName(), e.getMessage(), e);
                }
            }
        });
    }

    private void processEventHandlerMethod(final Object bean,
                                           final Method method,
                                           final RabbitEventHandler annotation,
                                           final String exchangeName,
                                           final ExchangeType exchangeType,
                                           final boolean autoCreate) {
        final String resolvedRoutingKey = routingKeyResolver.resolveRoutingKey(annotation.routingKey());

        // Determine queue name
        final String queueName = annotation.queue().isEmpty() ?
                infrastructure.defaultQueueName(exchangeName, resolvedRoutingKey) : annotation.queue();

        // Resolve full queue name with prefix
        final String resolvedQueueName = queueNameResolver.resolveQueueName(queueName);

        log.debug("Resolved queue: {}, routingKey: {}", resolvedQueueName, resolvedRoutingKey);

        if (autoCreate) {
            log.info("Auto-creating infrastructure for queue: {}", resolvedQueueName);
            // Setup Rabbit MQ infrastructure configuration
            infrastructure.setup(amqpAdmin, exchangeName, exchangeType, resolvedQueueName, resolvedRoutingKey);
        } else {
            log.info("Auto-creation disabled for queue: {}, ensure it exists manually", resolvedQueueName);
        }

        // Create the message listener
        createMessageListener(bean, method, resolvedQueueName, annotation.minConsumers(), annotation.maxConsumers());
    }

    /**
     * Creates and starts a message listener for the given method.
     */
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
            container.setErrorHandler(t -> {
                Throwable cause = t.getCause();
                if (cause instanceof com.fasterxml.jackson.databind.JsonMappingException ||
                        cause instanceof org.springframework.amqp.support.converter.MessageConversionException) {
                    log.warn("Invalid JSON message for queue '{}', skipping message: {}", queueName, cause.getMessage());
                } else {
                    log.error("Unexpected error in listener for queue '{}': {}", queueName, t.getMessage(), t);
                }
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
            log.info("Started message listener for queue: {} and method: {}", queueName, method.getName());
        } catch (Exception e) {
            log.error("Failed to create listener for queue {}: {}", queueName, e.getMessage(), e);
        }
    }

}