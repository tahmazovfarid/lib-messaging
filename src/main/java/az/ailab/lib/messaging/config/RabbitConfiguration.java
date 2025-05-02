package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.config.properties.RabbitExtendedProperties;
import az.ailab.lib.messaging.core.resolver.ExchangeNameResolver;
import az.ailab.lib.messaging.core.resolver.QueueNameResolver;
import az.ailab.lib.messaging.core.resolver.RoutingKeyResolver;
import az.ailab.lib.messaging.util.ApplicationContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that extends Spring Boot's autoconfiguration for RabbitMQ.
 *
 * <p>This class configures custom components required for the messaging infrastructure
 * and leverages Spring Boot's autoconfiguration for the standard RabbitMQ infrastructure.</p>
 *
 * @since 1.0
 * @author tahmazovfarid
 */
@Configuration
@EnableRabbit
@EnableConfigurationProperties(RabbitExtendedProperties.class)
@Slf4j
@RequiredArgsConstructor
public class RabbitConfiguration {

    private final RabbitExtendedProperties properties;

    /**
     * Creates a JSON message converter for converting Java objects to and from JSON.
     *
     * @return the message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*"); // Allow all packages for deserialization
        converter.setJavaTypeMapper(typeMapper);
        return converter;

    }

    /**
     * Creates a RabbitTemplate for sending messages to RabbitMQ.
     *
     * @param connectionFactory the connection factory
     * @param messageConverter  the message converter
     * @return the RabbitTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        template.setReturnsCallback((returned) -> {
            Message message = returned.getMessage();
            MessageProperties props = message.getMessageProperties();

            String messageId = props.getMessageId();
            String correlationId = props.getCorrelationId();
            String exchange = returned.getExchange();
            String routingKey = returned.getRoutingKey();
            int replyCode = returned.getReplyCode();
            String replyText = returned.getReplyText();
            String rawBody = new String(message.getBody(), StandardCharsets.UTF_8);
            String bodyPreview = rawBody.length() > 200
                    ? rawBody.substring(0, 200) + "…"
                    : rawBody;

            log.error(
                    """ 
                            Message DROPPED by broker (no binding for routingKey).
                            messageId={} correlationId={}
                            exchange='{}' routingKey='{}'
                            replyCode={} replyText='{}'
                            payloadPreview='{}'
                            """,
                    messageId, correlationId,
                    exchange, routingKey,
                    replyCode, replyText,
                    bodyPreview
            );
        });

        return template;
    }

    /**
     * Creates a RabbitAdmin for managing RabbitMQ exchanges, queues, and bindings.
     *
     * @param connectionFactory the connection factory
     * @return the RabbitAdmin
     */
    @Bean
    @ConditionalOnMissingBean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /**
     * Configures the SimpleRabbitListenerContainerFactory for creating message listener containers.
     *
     * @param connectionFactory the connection factory
     * @param messageConverter  the message converter
     * @return the configured factory
     */
    @Bean
    @ConditionalOnMissingBean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(10);
        return factory;
    }

    /**
     * Creates a QueueNameResolver for resolving queue names based on application context and configuration.
     *
     * @return the queue name resolver
     */
    @Bean
    public QueueNameResolver queueNameResolver() {
        return new QueueNameResolver(properties.getQueuePrefix());
    }

    /**
     * Creates an ExchangeNameResolver for resolving exchange names.
     *
     * @return the exchange name resolver
     */
    @Bean
    public ExchangeNameResolver exchangeNameResolver() {
        return new ExchangeNameResolver();
    }

    /**
     * Creates a RoutingKeyResolver for resolving routing keys.
     *
     * @return the routing key resolver
     */
    @Bean
    public RoutingKeyResolver routingKeyResolver() {
        return new RoutingKeyResolver();
    }

    /**
     * Creates an ApplicationContextProvider for accessing the application context.
     *
     * @return the application context provider
     */
    @Bean
    public ApplicationContextUtil applicationContextProvider() {
        return new ApplicationContextUtil();
    }

}