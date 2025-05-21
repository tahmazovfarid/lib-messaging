package az.ailab.lib.messaging.core.listener;

import az.ailab.lib.messaging.core.RabbitInfrastructure;
import az.ailab.lib.messaging.core.listener.adapter.ContainerListenerAdapter;
import az.ailab.lib.messaging.core.listener.annotation.RabbitEventHandler;
import az.ailab.lib.messaging.core.listener.annotation.RabbitEventListener;
import az.ailab.lib.messaging.core.listener.annotation.Retry;
import az.ailab.lib.messaging.core.listener.idempotency.IdempotencyService;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.QueueNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import az.ailab.lib.messaging.core.ExchangeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
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
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

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
                processEventHandlerAnnotations(bean, classAnnotation, exchangeName);
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
    private void processEventHandlerAnnotations(final Object bean, final RabbitEventListener classAnnotation, String exchangeName) {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            RabbitEventHandler methodAnnotation = AnnotationUtils.findAnnotation(method, RabbitEventHandler.class);

            if (methodAnnotation != null) {
                String routingKey = routingKeyResolver.resolveRoutingKey(methodAnnotation.routingKey());
                try {
                    log.debug("Registering routing '{}' for exchange '{}'", routingKey, exchangeName);
                    processEventHandlerMethod(bean, method, classAnnotation, methodAnnotation, exchangeName, routingKey);
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
     * @param bean             the instance of the class that contains the handler method
     * @param method           the method annotated with {@link RabbitEventHandler} to be invoked upon message reception
     * @param classAnnotation  the {@link RabbitEventListener} annotation present on the bean's class, providing exchange-level configuration
     * @param methodAnnotation the {@link RabbitEventHandler} annotation providing method-level queue and retry settings
     * @param exchangeName     the name of the exchange to which the queue should bind
     * @param routingKey       the routing key used to bind the queue to the exchange
     */
    private void processEventHandlerMethod(final Object bean,
                                           final Method method,
                                           final RabbitEventListener classAnnotation,
                                           final RabbitEventHandler methodAnnotation,
                                           final String exchangeName,
                                           final String routingKey) {

        // Resolve full queue name with prefix
        final String resolvedQueueName = queueNameResolver.resolveQueueName(exchangeName, routingKey, methodAnnotation.queue());
        ExchangeType exchangeType = classAnnotation.exchangeType();
        String dlxName = exchangeName + classAnnotation.dlxSuffix();
        String dlqName = resolvedQueueName + methodAnnotation.dlqSuffix();
        Retry retryConfig = methodAnnotation.retry();

        if (classAnnotation.autoCreate()) {
            log.debug("Auto-creating infrastructure for queue: {}", resolvedQueueName);
            infrastructure.setup(amqpAdmin, exchangeType, exchangeName, routingKey,
                    resolvedQueueName, dlxName, dlqName, retryConfig);
        } else {
            log.debug("Auto-creation disabled for queue: {}, ensure it exists manually", resolvedQueueName);
        }

        // Create the message listener
        createMessageListener(bean, method, exchangeName, routingKey, resolvedQueueName, dlxName,
                retryConfig, methodAnnotation.minConsumers(), methodAnnotation.maxConsumers()
        );
    }

    /**
     * Creates and starts a message listener container for the specified queue and handler method.
     * <p>
     * Configures message conversion, error handling, and optional concurrency settings.
     * </p>
     *
     * @param bean         the target bean instance to delegate message handling
     * @param method       the handler method to invoke on incoming messages
     * @param queueName    the name of the queue to listen to
     * @param minConsumers minimum number of concurrent consumers (0 to disable concurrency)
     * @param maxConsumers maximum number of concurrent consumers (0 to use minConsumers as max)
     */
    private void createMessageListener(final Object bean,
                                       final Method method,
                                       final String exchangeName,
                                       final String routingKey,
                                       final String queueName,
                                       final String dlxName,
                                       final Retry retryConfig,
                                       final int minConsumers,
                                       final int maxConsumers) {
        try {
            final var listenerAdapter = new ContainerListenerAdapter(bean, method, objectMapper, idempotencyService,
                    exchangeName, dlxName, routingKey, queueName, retryConfig
            );

            final var container = new SimpleMessageListenerContainer(connectionFactory);
            container.setQueueNames(queueName);
            container.setMessageListener(listenerAdapter);
            container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
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