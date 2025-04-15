package az.ailab.lib.messaging.core.resolver;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

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
     * @param queueName  the base queue name
     * @param routingKey the routing key associated with the queue
     * @return the resolved queue name with prefix applied
     * @throws IllegalArgumentException if the queue name is invalid
     */
    public String resolveQueueName(final String queueName, final String routingKey) {
        Assert.notNull(queueName, "Exchange name must not be null");

        if (queueName.isEmpty()) {
            throw new IllegalArgumentException("Queue name must not be empty");
        }

        final StringBuilder builder = new StringBuilder();

        // Apply prefix if exists
        if (!queuePrefix.isEmpty()) {
            builder.append(queuePrefix);
        }

        // Add base queue name
        builder.append(queueName);

        // Add routing key if provided
        if (routingKey != null && !routingKey.isEmpty()) {
            builder.append(".").append(routingKey);
        }

        return builder.toString();
    }

    /**
     * Resolves the queue name based on the provided queue name.
     *
     * @param queueName the base queue name
     * @return the resolved queue name with prefix applied
     */
    public String resolveQueueName(String queueName) {
        return resolveQueueName(queueName, null);
    }

    /**
     * Resolves a dead letter queue name for the given queue name.
     *
     * @param queueName             the original queue name
     * @param deadLetterQueueSuffix the suffix to append for dead letter queues
     * @return the dead letter queue name
     */
    public String resolveDeadLetterQueueName(String queueName, String deadLetterQueueSuffix) {
        return queueName + deadLetterQueueSuffix;
    }

}