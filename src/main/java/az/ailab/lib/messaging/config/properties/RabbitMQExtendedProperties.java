package az.ailab.lib.messaging.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Extended RabbitMQ configuration properties that complement the standard
 * Spring Boot RabbitMQ properties.
 *
 * <p>This class provides additional parameters to extend Spring Boot's RabbitMQ
 * configuration. These properties are configured under the "spring.rabbitmq.config"
 * prefix, which keeps them separate from the standard Spring Boot properties.</p>
 *
 * <p>Configuration example:</p>
 *
 * <pre>
 * // In application.yml
 * spring:
 *   rabbitmq:
 *     config:
 *       queue-prefix: expertise.user-service # {project_name}.{service_name}
 *       dead-letter-exchange-suffix: .dlx
 *       dead-letter-queue-suffix: .dlq
 * </pre>
 *
 * @since 0.0.1
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "spring.rabbitmq.config")
public class RabbitMQExtendedProperties {

    /**
     * Prefix used for all queues.
     *
     * <p>This prefix is automatically added to the beginning of all queue names.
     * It's useful for separating queues in different environments or services.</p>
     *
     * <p>For example, with a prefix of "order.", a queue named "created" would be
     * created as "order.created".</p>
     *
     * <p>Default value is an empty string.</p>
     */
    private String queuePrefix = "";

    /**
     * Suffix used for dead letter exchanges.
     *
     * <p>Dead letter exchanges (DLX) are used to handle messages that cannot be processed
     * or have expired. This suffix is added to the end of exchange names to create
     * their corresponding DLX names.</p>
     *
     * <p>For example, in the order processing system, a failed order message would be
     * sent to "order-events.dlx" exchange.</p>
     *
     * <p>Default value is ".dlx".</p>
     */
    private String deadLetterExchangeSuffix = ".dlx";

    /**
     * Suffix used for dead letter queues.
     *
     * <p>Dead letter queues are queues bound to dead letter exchanges, used for storing
     * messages that could not be processed. This suffix is added to the end of
     * queue names to create their corresponding dead letter queue names.</p>
     *
     * <p>For example, in the institution service, unprocessed institution update messages
     * might be routed to "institution.update.dlq".</p>
     *
     * <p>Default value is ".dlq".</p>
     */
    private String deadLetterQueueSuffix = ".dlq";

}