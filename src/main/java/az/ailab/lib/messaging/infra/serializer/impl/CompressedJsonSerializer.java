package az.ailab.lib.messaging.infra.serializer.impl;

import az.ailab.lib.messaging.infra.error.EventSerializationException;
import az.ailab.lib.messaging.core.enums.SerializationFormat;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import az.ailab.lib.messaging.infra.util.FormatUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50) // Medium priority
@ConditionalOnProperty(name = "spring.rabbitmq.config.serialization.compression.enabled", havingValue = "true")
@Slf4j
public class CompressedJsonSerializer implements EventSerializer {
    
    private final JsonEventSerializer jsonSerializer = new JsonEventSerializer();
    
    @Override
    public byte[] serialize(Object event) {
        try {
            byte[] jsonBytes = jsonSerializer.serialize(event);
            
            // Only compress if size > threshold
            if (jsonBytes.length > 512) { // 512 bytes threshold
                byte[] compressed = gzipCompress(FormatUtils.removeFormatMarker(jsonBytes));
                return FormatUtils.addFormatMarker(compressed, SerializationFormat.JSON_COMPRESSED);
            }
            
            return jsonBytes; // Return original JSON if too small
            
        } catch (Exception e) {
            log.warn("Compression failed for {}, falling back", event.getClass().getSimpleName(), e);
            return null;
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> eventClass) {
        try {
            byte[] compressed = FormatUtils.removeFormatMarker(data);
            byte[] jsonBytes = gzipDecompress(compressed);
            byte[] withMarker = FormatUtils.addFormatMarker(jsonBytes, SerializationFormat.JSON);
            
            return jsonSerializer.deserialize(withMarker, eventClass);
            
        } catch (Exception e) {
            throw new EventSerializationException("Compressed JSON deserialization failed for " +
                eventClass.getSimpleName(), e);
        }
    }
    
    @Override
    public boolean canHandle(Object event) {
        // Can handle any object for compression
        return true;
    }
    
    @Override
    public String getStrategyName() {
        return SerializationFormat.JSON_COMPRESSED.name();
    }
    
    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(data);
        }
        return output.toByteArray();
    }
    
    private byte[] gzipDecompress(byte[] data) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(input)) {
            gzip.transferTo(output);
        }
        return output.toByteArray();
    }

}