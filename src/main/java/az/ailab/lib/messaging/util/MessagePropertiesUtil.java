package az.ailab.lib.messaging.util;

import com.rabbitmq.client.AMQP;
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
        return new AMQP.BasicProperties.Builder()
                .contentType(props.getContentType())
                .contentEncoding(props.getContentEncoding())
                .headers(props.getHeaders())
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
