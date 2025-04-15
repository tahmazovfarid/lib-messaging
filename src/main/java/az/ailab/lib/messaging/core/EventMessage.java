package az.ailab.lib.messaging.core;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a message envelope that wraps a payload with metadata for event processing.
 *
 * <p>This class uses Java's record feature to create an immutable message object
 * that contains both the payload data and the metadata needed for message routing
 * and tracking.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Create a new event message with headers
 * Map<String, Object> headers = new HashMap<>();
 * headers.put("priority", "high");
 * headers.put("tenant", "client123");
 *
 * EventMessage<UserCreatedEvent> message = EventMessage.builder()
 *     .payload(userCreatedEvent)
 *     .source("user-service")
 *     .headers(headers)
 *     .build();
 * }</pre>
 *
 * @param <T> the type of payload this message contains
 */
public record EventMessage<T>(
        String id,

        Instant timestamp,

        T payload,

        String source,

        String correlationId,

        String version,

        Map<String, Object> headers
) {
    /**
     * Creates a new EventMessage with the specified parameters.
     * Validates that required fields are not null.
     * Ensures headers map is immutable.
     */
    public EventMessage {
        if (id == null) {
            throw new IllegalArgumentException("Message ID must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null");
        }

        // If headers is null, use empty map
        headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
    }

    /**
     * Returns a new builder for creating EventMessage instances.
     *
     * @param <T> the type of payload
     * @return a new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for creating EventMessage instances.
     *
     * @param <T> the type of payload
     */
    public static class Builder<T> {
        private String id = UUID.randomUUID().toString();
        private Instant timestamp = Instant.now();
        private T payload;
        private String source;
        private String correlationId;
        private String version = "1.0";
        private Map<String, Object> headers = new HashMap<>();

        /**
         * Sets the message ID.
         *
         * @param id the message ID
         * @return this builder
         */
        public Builder<T> id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the timestamp.
         *
         * @param timestamp the timestamp
         * @return this builder
         */
        public Builder<T> timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the payload.
         *
         * @param payload the payload
         * @return this builder
         */
        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Sets the source.
         *
         * @param source the source
         * @return this builder
         */
        public Builder<T> source(String source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the correlation ID.
         *
         * @param correlationId the correlation ID
         * @return this builder
         */
        public Builder<T> correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        /**
         * Sets the version.
         *
         * @param version the version
         * @return this builder
         */
        public Builder<T> version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the headers.
         *
         * @param headers the headers
         * @return this builder
         */
        public Builder<T> headers(Map<String, Object> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        /**
         * Adds a header.
         *
         * @param key   the header key
         * @param value the header value
         * @return this builder
         */
        public Builder<T> header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }

        /**
         * Builds the EventMessage.
         *
         * @return the EventMessage
         */
        public EventMessage<T> build() {
            return new EventMessage<>(id, timestamp, payload, source, correlationId, version, headers);
        }
    }

    /**
     * Creates a new basic EventMessage with default values for most fields.
     *
     * @param payload the message payload
     * @param source  the source service or component
     * @param <T>     the payload type
     * @return a new EventMessage instance
     */
    public static <T> EventMessage<T> create(T payload, String source) {
        return new EventMessage<>(
                UUID.randomUUID().toString(),
                Instant.now(),
                payload,
                source,
                null,
                "1.0",
                Collections.emptyMap()
        );
    }

    /**
     * Creates a new basic EventMessage with a correlation ID.
     *
     * @param payload       the message payload
     * @param source        the source service or component
     * @param correlationId the correlation ID
     * @param <T>           the payload type
     * @return a new EventMessage instance
     */
    public static <T> EventMessage<T> create(T payload, String source, String correlationId) {
        return new EventMessage<>(
                UUID.randomUUID().toString(),
                Instant.now(),
                payload,
                source,
                correlationId,
                "1.0",
                Collections.emptyMap()
        );
    }

    /**
     * Creates a new EventMessage with specified headers.
     *
     * @param payload the message payload
     * @param source  the source service or component
     * @param headers the message headers
     * @param <T>     the payload type
     * @return a new EventMessage instance
     */
    public static <T> EventMessage<T> create(T payload, String source, Map<String, Object> headers) {
        return new EventMessage<>(
                UUID.randomUUID().toString(),
                Instant.now(),
                payload,
                source,
                null,
                "1.0",
                headers != null ? headers : Collections.emptyMap()
        );
    }

    /**
     * Creates a new EventMessage with correlation ID and headers.
     *
     * @param payload       the message payload
     * @param source        the source service or component
     * @param correlationId the correlation ID
     * @param headers       the message headers
     * @param <T>           the payload type
     * @return a new EventMessage instance
     */
    public static <T> EventMessage<T> create(T payload, String source, String correlationId, Map<String, Object> headers) {
        return new EventMessage<>(
                UUID.randomUUID().toString(),
                Instant.now(),
                payload,
                source,
                correlationId,
                "1.0",
                headers != null ? headers : Collections.emptyMap()
        );
    }

    /**
     * Adds a header to this EventMessage and returns a new instance.
     *
     * @param key   the header key
     * @param value the header value
     * @return a new EventMessage with the added header
     */
    public EventMessage<T> withHeader(String key, Object value) {
        Map<String, Object> newHeaders = new HashMap<>(this.headers);
        newHeaders.put(key, value);
        return new EventMessage<>(id, timestamp, payload, source, correlationId, version, newHeaders);
    }

    /**
     * Adds multiple headers to this EventMessage and returns a new instance.
     *
     * @param additionalHeaders the headers to add
     * @return a new EventMessage with the added headers
     */
    public EventMessage<T> withHeaders(Map<String, Object> additionalHeaders) {
        Map<String, Object> newHeaders = new HashMap<>(this.headers);
        newHeaders.putAll(additionalHeaders);
        return new EventMessage<>(id, timestamp, payload, source, correlationId, version, newHeaders);
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