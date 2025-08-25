package az.ailab.lib.messaging.config.registrar;

import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import az.ailab.lib.messaging.infra.RabbitInfrastructure;
import az.ailab.lib.messaging.infra.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.infra.annotation.RabbitEventListener;
import az.ailab.lib.messaging.infra.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.infra.resolver.QueueNameResolver;
import az.ailab.lib.messaging.infra.resolver.RoutingKeyResolver;
import az.ailab.lib.messaging.infra.strategy.impl.ListenerStrategyFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Listens for Spring's ContextRefreshedEvent and automatically registers
 * RabbitMQ message listeners for beans annotated with {@link RabbitEventListener}.
 * <p>
 * Scans each listener bean for methods annotated with {@link RabbitEventHandler},
 * resolves exchange and queue names, optionally creates the necessary infrastructure,
 * and starts a {@link org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer} for each handler.
 * </p>
 *
 * <p>
 * This registrar centralizes event listener setup, ensuring consistent configuration
 * of exchanges, queues, bindings, error handling, and concurrency settings.
 * </p>
 *
 * @author tahmazovfarid
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RabbitEventHandlerRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ConnectionFactory connectionFactory;
    private final ExchangeNameResolver exchangeNameResolver;
    private final QueueNameResolver queueNameResolver;
    private final RoutingKeyResolver routingKeyResolver;
    private final AmqpAdmin amqpAdmin;
    private final RabbitInfrastructure infrastructure;
    private final ListenerStrategyFactory listenerStrategyFactory;

    /**
     * Handles the ContextRefreshedEvent after the Spring application context is initialized.
     * <p>
     * Scans for beans annotated with {@link RabbitEventListener}
     * and processes their event handler methods.
     * </p>
     *
     * @param event the ContextRefreshedEvent signaling context initialization
     */
    @Override
    public void onApplicationEvent(@NonNull final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        ctx.getBeansWithAnnotation(RabbitEventListener.class).values().forEach(bean -> {
            RabbitEventListener classAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), RabbitEventListener.class);
            if (classAnnotation != null) {
                String exchangeName = exchangeNameResolver.resolveExchangeName(classAnnotation.exchange());
                log.debug("Registering listener for exchange: '{}'", exchangeName);
                processEventHandlerAnnotations(bean, classAnnotation);
                log.debug("Registered listener for exchange: '{}'", exchangeName);
            }
        });
    }

    /**
     * Processes all methods of the given bean that are annotated with {@link RabbitEventHandler}.
     * <p>
     * Resolves exchange info from the class-level annotation and iterates through handler methods.
     * </p>
     *
     * @param bean            the target bean instance
     * @param classAnnotation the RabbitEventListener annotation present on the bean class
     */
    private void processEventHandlerAnnotations(final Object bean, final RabbitEventListener classAnnotation) {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            RabbitEventHandler methodAnnotation = AnnotationUtils.findAnnotation(method, RabbitEventHandler.class);

            if (methodAnnotation != null) {
                String exchangeName = classAnnotation.exchange();
                String routingKey = routingKeyResolver.resolveRoutingKey(methodAnnotation.routingKey());
                EventProcessingContext eventProcessingContext = EventProcessingContext.builder()
                        .bean(bean)
                        .method(method)
                        .listenerConfig(classAnnotation)
                        .handlerConfig(methodAnnotation)
                        .queue(queueNameResolver.resolveQueueName(exchangeName, routingKey, methodAnnotation.queue()))
                        .build();
                try {
                    log.debug("Registering routing '{}' for exchange '{}'", routingKey, exchangeName);
                    processEventHandlerMethod(eventProcessingContext);
                    log.debug("Registered routing '{}' for exchange: '{}'", routingKey, exchangeName);
                } catch (Exception e) {
                    log.error("Failed to register event handler routing key: '{}' due to: '{}'", routingKey, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Handles the registration of a RabbitMQ event handler method by resolving queue and exchange configurations,
     * conditionally creating the messaging infrastructure (exchange, queue, DLX, DLQ), and finally registering
     * the method as a message listener.
     * <p>
     * This method is typically called during application startup when scanning annotated components.
     *
     * @param eventProcessingContext the context which includes all data for processing
     */
    private void processEventHandlerMethod(final EventProcessingContext eventProcessingContext) {
        if (eventProcessingContext.isAutoCreateEnabled()) {
            log.debug("Auto-creating infrastructure for queue: {}", eventProcessingContext.queue());
            infrastructure.setup(amqpAdmin, eventProcessingContext);
        } else {
            log.debug("Auto-creation disabled for queue: {}, ensure it exists manually", eventProcessingContext.queue());
        }

        // Create the message listener
        createMessageListener(eventProcessingContext);
    }

    /**
     * Creates and starts a message listener container for the specified queue and handler method.
     * <p>
     * Configures message conversion, error handling, and optional concurrency settings.
     * </p>
     *
     * @param eventProcessingContext the context which includes all data for processing
     */
    private void createMessageListener(final EventProcessingContext eventProcessingContext) {
        try {
            final var listenerAdapter = listenerStrategyFactory.createAdapter(eventProcessingContext);

            final var container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(eventProcessingContext.queue());
            container.setMessageListener(listenerAdapter);
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            // Configure to not fail if queue doesn't exist yet
            container.setMissingQueuesFatal(false);
            container.setErrorHandler(t -> {
                log.error("Listener error on queue '{}', stopping container: {}", eventProcessingContext.queue(), t.getMessage(), t);
                container.stop();
            });

            // Configure concurrency if specified
            if (eventProcessingContext.minConsumers() > 0) {
                container.setConcurrentConsumers(eventProcessingContext.minConsumers());
                container.setMaxConcurrentConsumers(eventProcessingContext.maxConsumers());
                container.setPrefetchCount(eventProcessingContext.prefetchCount());

                log.debug("Configured concurrency for queue: {} with min: {}, max: {}, prefetch: {}",
                        eventProcessingContext.queue(), eventProcessingContext.minConsumers(), eventProcessingContext.maxConsumers(),
                        eventProcessingContext.prefetchCount());
            }

            container.start();
            log.info("Started message listener for queue '{}'", eventProcessingContext.queue());
        } catch (Exception e) {
            log.error("Failed to create listener for queue {}: {}", eventProcessingContext.queue(), e.getMessage(), e);
        }
    }

}