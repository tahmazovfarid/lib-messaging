package az.ailab.lib.messaging.config.properties;

import az.ailab.lib.messaging.core.enums.SerializationFormat;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the hybrid serialization pipeline used when
 * publishing and consuming RabbitMQ messages.
 *
 * <p>This class binds to properties under the prefix
 * {@code spring.rabbitmq.config.serialization} and controls which
 * serialization strategies are active, in what order they are attempted,
 * and when compression is applied.</p>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code avro-enabled} – enable or disable Avro-based serialization.</li>
 *   <li>{@code compression-enabled} – whether to wrap JSON output in a
 *       compression step when payloads exceed the threshold.</li>
 *   <li>{@code compression-threshold} – size in bytes above which
 *       JSON messages will be compressed if compression is enabled.</li>
 *   <li>{@code fallback-strategy} – the name of the strategy to use if
 *       all configured strategies fail; must be one of the
 *       {@link SerializationFormat} enum names.</li>
 *   <li>{@code strategy-priority} – an ordered list of strategy names
 *       indicating the order in which the serialization attempts are made.</li>
 * </ul>
 *
 * <p>Example YAML configuration:</p>
 * <pre>{@code
 * spring:
 *   rabbitmq:
 *     config:
 *       serialization:
 *         avro-enabled: true
 *         compression-enabled: true
 *         compression-threshold: 1024
 *         fallback-strategy: JSON # optional
 *         strategy-priority: # optional
 *           - AVRO
 *           - JSON_COMPRESSED
 *           - JSON
 * }</pre>
 *
 * @author tahmazovfarid
 * @since 1.0
 */
@ConfigurationProperties(prefix = "spring.rabbitmq.config.serialization")
@Getter
@Setter
public class HybridSerializationConfig {

    private boolean avroEnabled = false;           // Avro optional
    private boolean compressionEnabled = true;     // Compression default on
    private int compressionThreshold = 512;        // Compress if > 512 bytes
    private String fallbackStrategy = SerializationFormat.JSON.name();      // Always available fallback

    // Strategy priority configuration
    private List<String> strategyPriority = Arrays.asList(
            SerializationFormat.AVRO.name(),
            SerializationFormat.JSON_COMPRESSED.name(),
            SerializationFormat.JSON.name()
    );

}