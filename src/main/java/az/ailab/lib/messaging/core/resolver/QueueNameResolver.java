package az.ailab.lib.messaging.core.resolver;

import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;

/**
 * Utility class for resolving queue names based on provided parameters and configuration.
 *
 * <p>This class is responsible for constructing queue names by applying configured prefixes
 * and ensuring they follow naming conventions.</p>
 */
public class QueueNameResolver {

    @Getter
    private final String queuePrefix;

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    /**
     * Creates a new instance of QueueNameResolver.
     *
     * @param queuePrefix the prefix to apply to all queue names
     */
    public QueueNameResolver(String queuePrefix) {
        this.queuePrefix = queuePrefix != null ? queuePrefix : applicationName;
    }

    /**
     * Resolves the queue name based on the provided queue name and routing key.
     *
     * @param queue  the base queue name
     * @param routingKey the routing key associated with the queue
     * @return the resolved queue name with prefix applied
     * @throws IllegalArgumentException if the queue name is invalid
     */
    public String resolveQueueName(@NonNull final String exchangeName,
                                   @NonNull final String routingKey,
                                   final String queue) {
        final String queueName = StringUtils.isNotBlank(queue) ? queue : defaultQueueName(exchangeName, routingKey);

        if (StringUtils.isBlank(queueName)) {
            throw new IllegalArgumentException("Queue name must not be empty");
        }

        final StringBuilder builder = new StringBuilder();

        // Apply prefix if exists
        if (!queuePrefix.isEmpty()) {
            builder.append(queuePrefix);
        }

        // Add base queue name
        builder.append(queueName);

        return builder.toString();
    }

    /**
     * Resolves a dead letter queue name for the given queue name.
     *
     * @param queueName the original queue name
     * @param dlqSuffix the suffix to append for dead letter queues
     * @return the dead letter queue name
     */
    public String resolveDeadLetterQueueName(@NonNull String queueName, @NonNull String dlqSuffix) {
        return queueName + dlqSuffix;
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
    public String defaultQueueName(@NonNull String exchangeName, @NonNull String routingKey) {
        return exchangeName + "." + routingKey;
    }

}