package az.ailab.lib.messaging.infra.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Represents a generic event message wrapper that carries:
 * <ul>
 *   <li>a unique {@code id}</li>
 *   <li>a creation {@code timestamp}</li>
 *   <li>a payload of type {@code T}</li>
 *   <li>metadata about its origin and correlation</li>
 *   <li>optional delivery context (exchange, routing key, queue)</li>
 *   <li>arbitrary {@code headers}</li>
 * </ul>
 * <p>
 * This class is used throughout the messaging library to standardize how
 * event metadata is carried alongside the actual message payload.
 * </p>
 *
 * @param <T> the type of the message payload
 * @author tahmazov
 * @since 1.0
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EventMessage<T> {

    /**
     * Globally unique identifier for this message instance.
     */
    private String id;

    private Instant timestamp;
    private T payload;
    private String source;

    /**
     * Correlation identifier for tracing related messages in a workflow.
     */
    private String correlationId;

    /**
     * Schema or format version of this event message.
     */
    private String version;

    private Map<String, Object> headers;

    public EventMessage(final T payload,
                        final String source,
                        final String correlationId,
                        final Map<String, Object> headers) {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.payload = payload;
        this.source = source;
        this.correlationId = correlationId == null ? id : correlationId;
        this.headers = Map.copyOf(headers);
        this.version = "1.0";
    }

    public EventMessage(final T payload, final String source, final String correlationId) {
        this(payload, source, correlationId, Collections.emptyMap());
    }

    public EventMessage(final T payload, final String source, final Map<String, Object> headers) {
        this(payload, source, null, headers);
    }

    public EventMessage(final T payload, final String source) {
        this(payload, source, null, Collections.emptyMap());
    }

    /**
     * Gets a header value.
     *
     * @param key the header key
     * @return the header value, or null if not present
     */
    public Object getHeader(String key) {
        return headers.get(key);
    }

    /**
     * Gets a header value with a specific type.
     *
     * @param key  the header key
     * @param type the expected type of the header value
     * @param <V>  the type of the header value
     * @return the header value, or null if not present
     * @throws ClassCastException if the header value is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <V> V getHeader(String key, Class<V> type) {
        Object value = headers.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (V) value;
        }
        throw new ClassCastException("Header '" + key + "' is not of type " + type.getName());
    }

    /**
     * Checks if this EventMessage has a header with the specified key.
     *
     * @param key the header key
     * @return true if the header is present, false otherwise
     */
    public boolean hasHeader(String key) {
        return headers.containsKey(key);
    }

}