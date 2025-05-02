package az.ailab.lib.messaging.error;

import az.ailab.lib.messaging.core.EventMessage;
import lombok.Getter;

/**
 * Exception thrown when publishing an {@link EventMessage} to a RabbitMQ exchange fails.
 * <p>Captures the exchange name, routing key, and the original event message for diagnostics
 * and potential retry or dead-letter handling.</p>
 *
 * @author tahmazovfarid
 * @since 1.2
 */
@Getter
public class PublishFailureException extends Exception {

    /**
     * The name of the RabbitMQ exchange to which the publish was attempted.
     */
    private final String exchange;

    /**
     * The routing key used when attempting to publish the message.
     */
    private final String routingKey;

    /**
     * The original {@link EventMessage} that failed to publish.
     */
    private final EventMessage<?> eventMessage;

    /**
     * Creates a new {@code PublishFailureException}.
     *
     * @param exchange     the exchange name where the message was to be published
     * @param routingKey   the routing key used for the publish attempt
     * @param eventMessage the event message that could not be published
     */
    public PublishFailureException(String exchange, String routingKey, EventMessage<?> eventMessage) {
        super("Failed to publish message with id '" + eventMessage.getId() +
                "' to exchange '" + exchange + "' with routing key '" + routingKey + "'");
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.eventMessage = eventMessage;
    }

}
