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
 *       queue-durable: true
 *       exchange-durable: true
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

    /**
     * Default queue durable setting.
     *
     * <p>When set to "true", queues persist and survive broker restarts. This means
     * that even if the RabbitMQ broker is restarted, the queue and its messages won't
     * be lost, as the data is stored on disk.</p>
     *
     * <p>It's recommended to set this to "true" in production environments, especially
     * for critical services like order processing or file management.</p>
     *
     * <p>Default value is "true".</p>
     */
    private boolean queueDurable = true;

    /**
     * Default exchange durable setting.
     *
     * <p>When set to "true", exchanges persist and survive broker restarts. This ensures
     * that exchange configurations are not lost when the broker is restarted, eliminating
     * the need to recreate them.</p>
     *
     * <p>It's recommended to set this to "true" in production environments, particularly for
     * core message exchanges like those used between user and order services.</p>
     *
     * <p>Default value is "true".</p>
     */
    private boolean exchangeDurable = true;

    /**
     * Whether to automatically delete queues and exchanges when they are no longer in use.
     *
     * <p>When set to "true", queues and exchanges are automatically deleted when the last
     * consumer using them disconnects.</p>
     *
     * <p>This parameter can be useful for test and development environments, but is typically
     * set to "false" in production environments.</p>
     *
     * <p>For instance, in the procedural-type service testing environment, temporary queues
     * for test events might be configured with autoDelete=true.</p>
     *
     * <p>Default value is "false".</p>
     */
    private boolean autoDelete = false;

}