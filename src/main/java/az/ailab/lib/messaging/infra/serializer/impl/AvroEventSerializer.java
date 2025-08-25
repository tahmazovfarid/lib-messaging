package az.ailab.lib.messaging.infra.serializer.impl;

import az.ailab.lib.messaging.infra.error.EventSerializationException;
import az.ailab.lib.messaging.core.enums.SerializationFormat;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import az.ailab.lib.messaging.infra.util.FormatUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10) // High priority - use if available
@ConditionalOnProperty(name = "spring.rabbitmq.config.serialization.avro.enabled", havingValue = "true")
@Slf4j
public class AvroEventSerializer implements EventSerializer {
    
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
    
    @Override
    public byte[] serialize(Object event) {
        try {
            String eventType = event.getClass().getSimpleName();
            Schema schema = getSchema(eventType);
            
            if (schema == null) {
                return null; // Cannot handle - let other serializers try
            }
            
            // Avro serialization logic
            SpecificDatumWriter<Object> writer = new SpecificDatumWriter<>(schema);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
            
            writer.write(event, encoder);
            encoder.flush();
            
            byte[] avroBytes = outputStream.toByteArray();
            return FormatUtils.addFormatMarker(avroBytes, SerializationFormat.AVRO);
            
        } catch (Exception e) {
            log.warn("Avro serialization failed for {}, falling back to next strategy", 
                    event.getClass().getSimpleName(), e);
            return null; // Let other serializers handle it
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> eventClass) {
        try {
            byte[] avroBytes = FormatUtils.removeFormatMarker(data);
            Schema schema = getSchema(eventClass.getSimpleName());
            
            SpecificDatumReader<T> reader = new SpecificDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(avroBytes, null);
            
            return reader.read(null, decoder);
            
        } catch (Exception e) {
            throw new EventSerializationException("Avro deserialization failed for " +
                eventClass.getSimpleName(), e);
        }
    }
    
    @Override
    public boolean canHandle(Object event) {
        String eventType = event.getClass().getSimpleName();
        return getSchema(eventType) != null;
    }
    
    @Override
    public String getStrategyName() {
        return SerializationFormat.AVRO.name();
    }
    
    private Schema getSchema(String eventType) {
        return schemaCache.computeIfAbsent(eventType, this::loadSchema);
    }
    
    private Schema loadSchema(String eventType) {
        try {
            InputStream schemaStream = getClass().getResourceAsStream("/avro/" + eventType + ".avsc");
            if (schemaStream == null) {
                return null; // Schema not found - cannot handle
            }
            return new Schema.Parser().parse(schemaStream);
        } catch (Exception e) {
            log.debug("Schema not found for {}: {}", eventType, e.getMessage());
            return null;
        }
    }

}