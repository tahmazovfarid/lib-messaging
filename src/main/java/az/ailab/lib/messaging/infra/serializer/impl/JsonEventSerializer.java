package az.ailab.lib.messaging.infra.serializer.impl;

import static az.ailab.lib.messaging.infra.util.FormatUtils.addFormatMarker;
import static az.ailab.lib.messaging.infra.util.FormatUtils.removeFormatMarker;

import az.ailab.lib.messaging.infra.error.EventSerializationException;
import az.ailab.lib.messaging.core.enums.SerializationFormat;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link EventSerializer} using Jackson-based JSON serialization.
 *
 * <p>This serializer handles conversion of event objects to and from JSON format. It adds a
 * format marker (defined in {@link SerializationFormat#JSON}) to each serialized payload for
 * dynamic format detection during deserialization.</p>
 *
 * <p>It serves as the fallback and universal strategy since JSON is capable of handling most
 * Java objects, including those containing Java 8 time classes.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Supports all object types (catch-all strategy)</li>
 *   <li>Serializes using UTF-8 encoded JSON</li>
 *   <li>Ignores unknown properties on deserialization</li>
 *   <li>Serializes date/time using ISO format (not timestamps)</li>
 *   <li>Adds and detects format marker for hybrid deserialization logic</li>
 * </ul>
 *
 * <h2>Fallback Logic:</h2>
 * If serialization or deserialization fails, a domain-specific {@link EventSerializationException}
 * is thrown with useful context for debugging.
 *
 * <p><b>Note:</b> This implementation is the only one active by default. Other formats such as
 * AVRO, Protobuf, or compressed JSON are reserved for future extension and are not currently wired.</p>
 *
 * <h2>Usage:</h2>
 * Used internally by the event replay mechanism, message consumers, or any other framework component
 * requiring conversion between raw byte arrays and POJOs.
 *
 * @author tahmazovfarid
 * @see SerializationFormat#JSON
 * @see EventSerializer
 * @see EventSerializationException
 */
@Component
@Order(100)
public class JsonEventSerializer implements EventSerializer {
    
    private final ObjectMapper objectMapper;

    /**
     * Initializes the Jackson ObjectMapper with custom configuration:
     * <ul>
     *   <li>Disables failure on unknown properties</li>
     *   <li>Disables timestamp-style date serialization</li>
     *   <li>Registers {@link JavaTimeModule} for Java 8 date/time support</li>
     * </ul>
     */
    public JsonEventSerializer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Serializes the given event object to a UTF-8 encoded JSON byte array,
     * prepending a format marker defined by {@link SerializationFormat#JSON}.
     *
     * @param event the event object to serialize
     * @return the serialized byte array including a format marker
     * @throws EventSerializationException if serialization fails
     */
    @Override
    public byte[] serialize(Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            return addFormatMarker(jsonBytes, SerializationFormat.JSON);
        } catch (Exception e) {
            throw new EventSerializationException("JSON serialization failed for " +
                    event.getClass().getSimpleName(), e);
        }
    }

    /**
     * Deserializes the given byte array (with format marker) to the target event class.
     *
     * @param data       the serialized byte array including format marker
     * @param eventClass the target Java class to deserialize into
     * @param <T>        the event type
     * @return the deserialized object
     * @throws EventSerializationException if deserialization fails
     */
    @Override
    public <T> T deserialize(byte[] data, Class<T> eventClass) {
        try {
            byte[] jsonBytes = removeFormatMarker(data);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, eventClass);
        } catch (Exception e) {
            throw new EventSerializationException("JSON deserialization failed for " +
                    eventClass.getSimpleName(), e);
        }
    }

    /**
     * Indicates that this serializer can handle any object.
     *
     * @param event the event object to check
     * @return always true
     */
    @Override
    public boolean canHandle(Object event) {
        return true;
    }

    /**
     * Returns the name of this serialization strategy ("JSON").
     *
     * @return strategy name
     */
    @Override
    public String getStrategyName() {
        return SerializationFormat.JSON.name();
    }

}