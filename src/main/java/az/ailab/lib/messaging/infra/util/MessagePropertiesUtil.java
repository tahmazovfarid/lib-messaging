package az.ailab.lib.messaging.infra.util;

import com.rabbitmq.client.AMQP;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

public final class MessagePropertiesUtil {

    private MessagePropertiesUtil() {

    }

    /**
     * Convert Spring AMQP {@link MessageProperties} into RabbitMQ client's
     * {@link AMQP.BasicProperties.Builder buildable} form.
     *
     * @param props Spring AMQP message properties
     * @return AMQP client BasicProperties
     */
    public static AMQP.BasicProperties toBasicProperties(MessageProperties props) {
        return toBasicProperties(props, Map.of());
    }

    /**
     * Convert Spring AMQP {@link MessageProperties} into RabbitMQ client's
     * {@link AMQP.BasicProperties.Builder buildable} form.
     *
     * @param props Spring AMQP message properties
     * @return AMQP client BasicProperties
     */
    public static AMQP.BasicProperties toBasicProperties(MessageProperties props, Map<String, Object> additionalHeaders) {
        var allHeaders  = new HashMap<>(additionalHeaders);
        allHeaders.putAll(props.getHeaders());

        return new AMQP.BasicProperties.Builder()
                .contentType(props.getContentType())
                .contentEncoding(props.getContentEncoding())
                .headers(allHeaders)
                .deliveryMode(props.getDeliveryMode() == MessageDeliveryMode.PERSISTENT ? 2 : 1)
                .priority(props.getPriority())
                .correlationId(props.getCorrelationId())
                .replyTo(props.getReplyTo())
                .expiration(props.getExpiration())
                .messageId(props.getMessageId())
                .timestamp(props.getTimestamp())
                .type(props.getType())
                .userId(props.getUserId())
                .appId(props.getAppId())
                .clusterId(props.getClusterId())
                .build();
    }

}
