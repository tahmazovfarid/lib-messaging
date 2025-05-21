package az.ailab.lib.messaging.constants;

/**
 * Constants for RabbitMQ header names used by the messaging library.
 * <p>Centralizes all header key names to avoid string duplication and typos.</p>
 * @since 1.2
 * @author tahmazovfarid
 */
public final class RabbitHeaders {

    private RabbitHeaders() {
        // Utility class, prevent instantiation
    }

    public static final String X_DEATH = "x-death";

    public static final String DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";
    public static final String RETRY_MESSAGE_TTL = "x-message-ttl";
    public static final String EXCEPTION_ORIGIN = "x-exception-origin";
    public static final String EXCEPTION_TYPE = "x-exception -type";
    public static final String EXCEPTION_MESSAGE = "x-exception-message";
    public static final String FAILURE_REASON = "x-failure-reason";

}