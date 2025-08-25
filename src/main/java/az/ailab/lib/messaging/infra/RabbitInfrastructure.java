package az.ailab.lib.messaging.infra;

import az.ailab.lib.messaging.infra.constants.RabbitHeaders;
import az.ailab.lib.messaging.infra.annotation.Retryable;
import az.ailab.lib.messaging.core.enums.ExchangeType;
import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

/**
 * Core component for programmatic RabbitMQ infrastructure provisioning.
 *
 * <p>This class declares and manages AMQP entities such as exchanges, queues, and bindings based on
 * given business and architectural rules. It supports both standard messaging flows and advanced features like:</p>
 * <ul>
 *     <li><strong>Dead-letter queues (DLQ)</strong> for handling failed messages</li>
 *     <li><strong>Native retry infrastructure</strong> using TTL + DLX mechanism</li>
 *     <li><strong>Dynamic exchange/queue/binding declaration</strong> with in-memory caching</li>
 * </ul>
 *
 * <p>By abstracting these operations, this class reduces duplication and ensures that queue-related conventions
 * (like suffixes and naming) are consistently applied across the system.</p>
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * RabbitInfrastructure infra = new RabbitInfrastructure();
 * infra.setup(
 *     amqpAdmin,
 *     eventMetadata
 * );
 * }</pre>
 *
 * <p>The above will declare:</p>
 * <ul>
 *     <li>Topic exchange: <code>order-events</code></li>
 *     <li>Routing key: <code>created</code></li>
 *     <li>Main queue: <code>expertise.ms-user.order-events.created</code></li>
 *     <li>DLQ: <code>expertise.ms-user.order-events.created.dlq</code> bound to <code>order-events.dlx</code></li>
 *     <li>If retry is enabled:
 *         <ul>
 *             <li>Retry exchange: <code>order-events.retry</code></li>
 *             <li>Retry queue: <code>expertise.ms-user.order-events.created.retry</code> with TTL and DLX routing</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>All declarations are safe to call repeatedly and are idempotent via internal caching.</p>
 *
 * @since 1.0
 * @author tahmazovfarid
 * @see Retryable
 */
@Slf4j
public class RabbitInfrastructure {

    public static final boolean EXCHANGE_DURABLE = true;
    public static final boolean EXCHANGE_AUTO_DELETE = false;

    public static final boolean QUEUE_DURABLE = true;
    public static final boolean QUEUE_EXCLUSIVE = false;
    public static final boolean QUEUE_AUTO_DELETE = false;

    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();

    /**
     * Creates or retrieves the specified exchange, queue, and binding in RabbitMQ.
     * <p>If {@code queueName} is blank, a default name is generated based on
     * {@code exchangeName} and {@code routingKey}.</p>
     *
     * @param amqpAdmin    the AmqpAdmin used to declare resources
     * @param eventProcessingContext the metadata of the event
     * @throws IllegalArgumentException if {@code exchangeName} or {@code routingKey} is null or empty
     */
    public void setup(final AmqpAdmin amqpAdmin,
                      final EventProcessingContext eventProcessingContext) {
        final Exchange exchange = declareExchange(amqpAdmin, eventProcessingContext.exchange(), eventProcessingContext.exchangeType());
        final Exchange dlx = declareExchange(amqpAdmin, eventProcessingContext.dlxName(), ExchangeType.DIRECT);
        final Queue dlq = declareQueue(amqpAdmin, eventProcessingContext.dlqName());
        declareBinding(amqpAdmin, dlx, dlq, eventProcessingContext.routingKey(), ExchangeType.DIRECT);

        if (eventProcessingContext.isRetryableEnabled()) {
            log.warn("Retry configuration is DISABLED for exchange='{}', routingKey='{}', queue='{}'. " +
                            "Please, enable retry mechanism before deploying to production!",
                    eventProcessingContext.exchange(), eventProcessingContext.routingKey(), eventProcessingContext.queue());

            // no retry → simple queue + binding + dlx‐exchange + dlq‐queue
            final Queue queue = declareQueue(amqpAdmin, eventProcessingContext.queue());
            declareBinding(amqpAdmin, exchange, queue, eventProcessingContext.routingKey(), eventProcessingContext.exchangeType());
            return;
        }

        setupWithRetryInfra(amqpAdmin, eventProcessingContext);
    }

    /**
     * Sets up RabbitMQ retry infrastructure for a given queue and exchange by creating
     * a retry exchange and retry queue with TTL and dead-lettering back to the original exchange.
     *
     * <p>This method enables a two-level message redelivery strategy:</p>
     * <ol>
     *   <li> Failed messages from the main queue are dead-lettered to a retry exchange.</li>
     *   <li> The retry exchange routes them to a retry queue with a TTL.</li>
     *   <li> After the TTL expires, messages are dead-lettered back to the original exchange and routing key.</li>
     *   <li> Messages are reprocessed by the original queue after delay, simulating backoff.</li>
     * </ol>
     *
     * <p>This design avoids consumer-side scheduling and leverages native RabbitMQ capabilities
     * (DLX, TTL, routing) to implement retry with delayed reprocessing.</p>
     *
     * @param amqpAdmin       the AMQP admin to declare exchanges, queues, and bindings
     * @param eventProcessingContext the metadata of the event
     *
     * @see Retryable
     * @since 1.2
     */
    private void setupWithRetryInfra(AmqpAdmin amqpAdmin,
                                     EventProcessingContext eventProcessingContext) {
        String retryExName = eventProcessingContext.retryXSuffix();
        String retryQueueName = eventProcessingContext.retryQSuffix();

        // retry‐exchange + retry‐queue (TTL → main exchange)
        Exchange retryX = declareExchange(amqpAdmin, retryExName, ExchangeType.DIRECT);
        Queue retryQ = declareQueue(amqpAdmin, retryQueueName, Map.of(
                RabbitHeaders.DEAD_LETTER_EXCHANGE, eventProcessingContext.exchange(),
                RabbitHeaders.DEAD_LETTER_ROUTING_KEY, eventProcessingContext.routingKey(),
                RabbitHeaders.RETRY_MESSAGE_TTL, eventProcessingContext.retryable().fixedDelay())
        );
        declareBinding(amqpAdmin, retryX, retryQ, eventProcessingContext.routingKey(), ExchangeType.DIRECT);

        // main‐queue with DLX → retryEx
        Queue queue = declareQueue(amqpAdmin, eventProcessingContext.queue(), Map.of(
                RabbitHeaders.DEAD_LETTER_EXCHANGE, retryExName,
                RabbitHeaders.DEAD_LETTER_ROUTING_KEY, eventProcessingContext.routingKey())
        );
        Exchange exchange = exchanges.get(eventProcessingContext.exchange());
        declareBinding(amqpAdmin, exchange, queue, eventProcessingContext.routingKey(), eventProcessingContext.exchangeType());
    }

    /**
     * Declares or retrieves a durable, non-auto-delete exchange of the given type.
     *
     * @param amqpAdmin    the AmqpAdmin to declare the exchange
     * @param exchangeName the name of the exchange
     * @param exchangeType the type of exchange to declare
     * @return the existing or newly declared Exchange instance
     */
    public Exchange declareExchange(AmqpAdmin amqpAdmin,
                                    String exchangeName,
                                    ExchangeType exchangeType) {

        if (exchanges.containsKey(exchangeName)) {
            return exchanges.get(exchangeName);
        }

        Exchange exchange = switch (exchangeType) {
            case DIRECT -> new DirectExchange(exchangeName, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE);
            case FANOUT -> new FanoutExchange(exchangeName, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE);
            default -> new TopicExchange(exchangeName, EXCHANGE_DURABLE, EXCHANGE_AUTO_DELETE);
        };

        amqpAdmin.declareExchange(exchange);
        exchanges.put(exchangeName, exchange);

        log.trace("Declared exchange: {} of type: {}", exchangeName, exchangeType);
        return exchange;
    }

    /**
     * Declares or retrieves a durable, non-exclusive, non-auto-delete queue with no arguments.
     *
     * @param amqpAdmin the AmqpAdmin to declare the queue
     * @param queueName the name of the queue
     * @return the existing or newly declared Queue instance
     */
    public Queue declareQueue(AmqpAdmin amqpAdmin, String queueName) {
        return declareQueue(amqpAdmin, queueName, null);
    }

    /**
     * Declares or retrieves a durable, non-exclusive, non-auto-delete queue with optional arguments.
     *
     * @param amqpAdmin the AmqpAdmin to declare the queue
     * @param queueName the name of the queue
     * @param arguments optional queue arguments (e.g., TTL, dead-letter settings)
     * @return the existing or newly declared Queue instance
     */
    public Queue declareQueue(AmqpAdmin amqpAdmin, String queueName, Map<String, Object> arguments) {
        if (queues.containsKey(queueName)) {
            return queues.get(queueName);
        }

        Queue queue = new Queue(
                queueName,
                QUEUE_DURABLE,
                QUEUE_EXCLUSIVE,
                QUEUE_AUTO_DELETE,
                arguments != null ? arguments : Map.of()
        );

        amqpAdmin.declareQueue(queue);
        queues.put(queueName, queue);

        log.trace("Declared queue: {}", queueName);
        return queue;
    }

    /**
     * Creates and declares a binding between the given exchange and queue using the specified routing key.
     * For FANOUT exchanges, the routing key is ignored.
     *
     * @param amqpAdmin    the AmqpAdmin to declare the binding
     * @param exchange     the Exchange to bind from
     * @param queue        the Queue to bind to
     * @param routingKey   the routing key for binding (ignored for FANOUT)
     * @param exchangeType the type of exchange
     */
    public void declareBinding(AmqpAdmin amqpAdmin,
                               Exchange exchange,
                               Queue queue,
                               String routingKey,
                               ExchangeType exchangeType) {
        Binding binding = switch (exchangeType) {
            case DIRECT -> BindingBuilder.bind(queue).to((DirectExchange) exchange).with(routingKey);
            case FANOUT -> BindingBuilder.bind(queue).to((FanoutExchange) exchange);
            default -> BindingBuilder.bind(queue).to((TopicExchange) exchange).with(routingKey);
        };

        amqpAdmin.declareBinding(binding);

        log.trace("Declared binding between exchange: {} and queue: {} with routing key: {}",
                exchange.getName(), queue.getName(), routingKey);
    }

}
