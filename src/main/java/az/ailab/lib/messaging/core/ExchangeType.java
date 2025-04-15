package az.ailab.lib.messaging.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the types of exchanges that can be created in RabbitMQ.
 *
 * <p>Exchange types determine how messages are routed from
 * exchanges to queues based on routing keys and bindings.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ExchangeType {

    /**
     * Direct exchange routes messages to queues based on an exact match of the routing key.
     *
     * <p>If the routing key of a published message exactly matches the binding key of a queue,
     * the message is delivered to that queue.</p>
     */
    DIRECT("direct"),

    /**
     * Topic exchange routes messages to queues based on pattern matching of the routing key.
     *
     * <p>Binding keys can include wildcards: '*' for a single word, '#' for zero or more words.
     * For example, "orders.*.created" would match "orders.retail.created" but not "orders.retail.wholesale.created".</p>
     */
    TOPIC("topic"),

    /**
     * Fanout exchange broadcasts messages to all bound queues regardless of routing key.
     *
     * <p>This is useful for scenarios where the same message needs to be processed by multiple consumers,
     * such as broadcasting notifications to all users.</p>
     */
    FANOUT("fanout"),

    /**
     * Headers exchange routes messages based on message header values rather than routing keys.
     *
     * <p>This allows for more complex routing logic based on multiple criteria.</p>
     */
    HEADERS("headers");

    private final String type;

}