package az.ailab.lib.messaging.core;

import io.micrometer.common.util.StringUtils;
import java.util.HashMap;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Provides infrastructure setup for RabbitMQ: declaring exchanges, queues, and bindings
 * with sensible defaults. Offers methods to create or retrieve existing exchanges and queues
 * and to bind them according to the specified routing strategies.
 *
 * <p>Typical usage:</p>
 * <pre>
 *   RabbitInfrastructure infra = new RabbitInfrastructure();
 *   infra.setup(amqpAdmin, "orders.exchange", ExchangeType.TOPIC, "", "order.created");
 * </pre>
 *
 * @author tahmazovfarid
 */
@Configuration
@Slf4j
public class RabbitInfrastructure {

    public static final boolean EXCHANGE_DURABLE = true;
    public static final boolean EXCHANGE_AUTO_DELETE = false;

    public static final boolean QUEUE_DURABLE = true;
    public static final boolean QUEUE_EXCLUSIVE = false;
    public static final boolean QUEUE_AUTO_DELETE = false;

    public static final String DEFAULT_ROUTING_KEY = "#";

    @Value("${spring.application.name:unknown}")
    private String serviceName;

    @Value("${spring.profiles.active:default}")
    private String environment;

    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();

    /**
     * Creates or retrieves the specified exchange, queue, and binding in RabbitMQ.
     * <p>If {@code queueName} is blank, a default name is generated based on
     * {@code exchangeName} and {@code routingKey}.</p>
     *
     * @param amqpAdmin    the AmqpAdmin used to declare resources
     * @param exchangeName the name of the exchange to declare or reuse
     * @param exchangeType the type of exchange (DIRECT, FANOUT, TOPIC)
     * @param queueName    optional name of the queue; if blank, a default is generated
     * @param routingKey   the routing key to bind the queue to the exchange
     * @throws IllegalArgumentException if {@code exchangeName} or {@code routingKey} is null or empty
     */
    public void setup(AmqpAdmin amqpAdmin,
                      String exchangeName,
                      ExchangeType exchangeType,
                      String queueName,
                      String routingKey) {
        if (StringUtils.isBlank(exchangeName) || StringUtils.isBlank(routingKey)) {
            throw new IllegalArgumentException("Exchange name and routing key must be provided");
        }
        Exchange exchange = createExchange(amqpAdmin, exchangeName, exchangeType);

        if (StringUtils.isBlank(queueName)) {
            queueName = defaultQueueName(exchangeName, routingKey);
        }

        Queue queue = createQueue(amqpAdmin, queueName);
        createBinding(amqpAdmin, exchange, queue, routingKey, exchangeType);
    }

    /**
     * Declares or retrieves a durable, non-auto-delete exchange of the given type.
     *
     * @param amqpAdmin    the AmqpAdmin to declare the exchange
     * @param exchangeName the name of the exchange
     * @param exchangeType the type of exchange to create
     * @return the existing or newly declared Exchange instance
     */
    public Exchange createExchange(AmqpAdmin amqpAdmin,
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

        log.debug("Created exchange: {} of type: {}", exchangeName, exchangeType);
        return exchange;
    }

    /**
     * Declares or retrieves a durable, non-exclusive, non-auto-delete queue with no arguments.
     *
     * @param amqpAdmin the AmqpAdmin to declare the queue
     * @param queueName the name of the queue
     * @return the existing or newly declared Queue instance
     */
    public Queue createQueue(AmqpAdmin amqpAdmin, String queueName) {
        return createQueue(amqpAdmin, queueName, null);
    }

    /**
     * Declares or retrieves a durable, non-exclusive, non-auto-delete queue with optional arguments.
     *
     * @param amqpAdmin the AmqpAdmin to declare the queue
     * @param queueName the name of the queue
     * @param arguments optional queue arguments (e.g., TTL, dead-letter settings)
     * @return the existing or newly declared Queue instance
     */
    public Queue createQueue(AmqpAdmin amqpAdmin, String queueName, Map<String, Object> arguments) {
        if (queues.containsKey(queueName)) {
            return queues.get(queueName);
        }

        Queue queue = new Queue(
                queueName,
                QUEUE_DURABLE,
                QUEUE_EXCLUSIVE,
                QUEUE_AUTO_DELETE,
                arguments != null ? arguments : new HashMap<>()
        );

        amqpAdmin.declareQueue(queue);
        queues.put(queueName, queue);

        log.debug("Declared queue: {}", queueName);
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
    public void createBinding(AmqpAdmin amqpAdmin,
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

        log.debug("Created binding between exchange: {} and queue: {} with routing key: {}",
                exchange.getName(), queue.getName(), routingKey);
    }

    /**
     * Generates a default queue name based on exchange name and routing key.
     * <p>This method follows a consistent naming convention for queues:</p>
     * <ul>
     *   <li>For wildcard routing keys (#): {@code exchangeName.all}</li>
     *   <li>For specific routing keys: {@code exchangeName.routingKey}</li>
     * </ul>
     *
     * <p>Example usages:</p>
     * <pre>
     * defaultQueueName("orders", "#") = "orders.all"
     * defaultQueueName("notifications", "email.sent") = "notifications.email.sent"
     * </pre>
     *
     * @param exchangeName The name of the exchange
     * @param routingKey   The routing key used for the binding
     * @return A consistently formatted queue name
     */
    public String defaultQueueName(String exchangeName, String routingKey) {
        if (DEFAULT_ROUTING_KEY.equals(routingKey)) {
            return exchangeName + ".all";
        }
        return exchangeName + "." + routingKey;
    }

    public String serviceSpecificQueueName(String baseName) {
        return baseName + "." + serviceName + "." + environment;
    }

}
