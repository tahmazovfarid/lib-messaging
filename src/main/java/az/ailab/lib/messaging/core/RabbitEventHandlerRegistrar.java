package az.ailab.lib.messaging.core;

import az.ailab.lib.messaging.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.annotation.RabbitEventListener;
import az.ailab.lib.messaging.core.adapter.PayloadAwareMessageListenerAdapter;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.QueueNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Listens for Spring's ContextRefreshedEvent and automatically registers
 * RabbitMQ message listeners for beans annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventListener}.
 * <p>
 * Scans each listener bean for methods annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventHandler},
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
    private final MessageConverter messageConverter;
    private final ExchangeNameResolver exchangeNameResolver;
    private final QueueNameResolver queueNameResolver;
    private final RoutingKeyResolver routingKeyResolver;
    private final AmqpAdmin amqpAdmin;
    private final RabbitInfrastructure infrastructure;
    private final ObjectMapper objectMapper;

    /**
     * Handles the ContextRefreshedEvent after the Spring application context is initialized.
     * <p>
     * Scans for beans annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventListener}
     * and processes their event handler methods.
     * </p>
     *
     * @param event the ContextRefreshedEvent signaling context initialization
     */
    @Override
    public void onApplicationEvent(@NonNull final ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        log.debug("Starting RabbitMQ listener registration process");

        ctx.getBeansWithAnnotation(RabbitEventListener.class).values().forEach(bean -> {
            RabbitEventListener classAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), RabbitEventListener.class);
            if (classAnnotation != null) {
                log.debug("Processing bean: {} with RabbitEventListener", bean.getClass().getName());
                processEventHandlerAnnotations(bean, classAnnotation);
            }
        });
    }

    /**
     * Processes all methods of the given bean that are annotated with {@link az.ailab.lib.messaging.annotation.RabbitEventHandler}.
     * <p>
     * Resolves exchange info from the class-level annotation and iterates through handler methods.
     * </p>
     *
     * @param bean             the target bean instance
     * @param classAnnotation  the RabbitEventListener annotation present on the bean class
     */
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

    /**
     * Processes a single handler method: resolves queue details, optionally auto-creates infrastructure,
     * and registers the message listener.
     *
     * @param bean            the target bean instance
     * @param method          the handler method to invoke on message receive
     * @param annotation      the RabbitEventHandler annotation on the method
     * @param exchangeName    the resolved exchange name
     * @param exchangeType    the type of exchange to bind
     * @param autoCreate      flag indicating whether to auto-create queues/exchanges
     */
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
            log.debug("Auto-creating infrastructure for queue: {}", resolvedQueueName);
            // Setup Rabbit MQ infrastructure configuration
            infrastructure.setup(amqpAdmin, exchangeName, exchangeType, resolvedQueueName, resolvedRoutingKey);
        } else {
            log.debug("Auto-creation disabled for queue: {}, ensure it exists manually", resolvedQueueName);
        }

        // Create the message listener
        createMessageListener(bean, method, resolvedQueueName, annotation.minConsumers(), annotation.maxConsumers());
    }

    /**
     * Creates and starts a message listener container for the specified queue and handler method.
     * <p>
     * Configures message conversion, error handling, and optional concurrency settings.
     * </p>
     *
     * @param delegate      the target bean instance to delegate message handling
     * @param method        the handler method to invoke on incoming messages
     * @param queueName     the name of the queue to listen to
     * @param minConsumers  minimum number of concurrent consumers (0 to disable concurrency)
     * @param maxConsumers  maximum number of concurrent consumers (0 to use minConsumers as max)
     */
    private void createMessageListener(final Object delegate,
                                       final Method method,
                                       final String queueName,
                                       final int minConsumers,
                                       final int maxConsumers) {
        try {
            final MessageListenerAdapter listenerAdapter =
                    new PayloadAwareMessageListenerAdapter(queueName, objectMapper, delegate, messageConverter, method);

            final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(queueName);
            container.setMessageListener(listenerAdapter);
            // Configure to not fail if queue doesn't exist yet
            container.setMissingQueuesFatal(false);
            container.setErrorHandler(t -> {
                log.error("Listener error on queue '{}', stopping container: {}", queueName, t.getMessage(), t);
                container.stop();
            });

            // Configure concurrency if specified
            if (minConsumers > 0) {
                container.setConcurrentConsumers(minConsumers);

                final int effectiveMaxConsumers = maxConsumers > 0 ? maxConsumers : minConsumers;
                container.setMaxConcurrentConsumers(effectiveMaxConsumers);

                log.debug("Configured concurrency for queue: {} with min: {} and max: {}",
                        queueName, minConsumers, effectiveMaxConsumers);
            }

            container.start();
            log.info("Started message listener for queue '{}'", queueName);
        } catch (Exception e) {
            log.error("Failed to create listener for queue {}: {}", queueName, e.getMessage(), e);
        }
    }

}