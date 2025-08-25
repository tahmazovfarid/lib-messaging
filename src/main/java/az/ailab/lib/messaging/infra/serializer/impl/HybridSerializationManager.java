package az.ailab.lib.messaging.infra.serializer.impl;

import az.ailab.lib.messaging.infra.error.EventSerializationException;
import az.ailab.lib.messaging.core.enums.SerializationFormat;
import az.ailab.lib.messaging.infra.serializer.EventSerializer;
import az.ailab.lib.messaging.infra.util.FormatUtils;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Primary
@Component
@Slf4j
public class HybridSerializationManager implements EventSerializer {
    
    private final List<EventSerializer> serializers;
    
    public HybridSerializationManager(List<EventSerializer> serializers) {
        // Spring auto-injects all EventSerializer beans ordered by @Order
        this.serializers = serializers.stream()
            .filter(s -> !(s instanceof HybridSerializationManager)) // Exclude self
            .sorted(Comparator.comparing(s -> s.getClass().getAnnotation(Order.class).value()))
            .collect(Collectors.toList());
            
        log.info("Initialized hybrid serialization with strategies: {}", 
            serializers.stream().map(EventSerializer::getStrategyName).collect(Collectors.toList()));
    }
    
    @Override
    public byte[] serialize(Object event) {
        
        // Try serializers in priority order
        for (EventSerializer serializer : serializers) {
            if (serializer.canHandle(event)) {
                try {
                    byte[] result = serializer.serialize(event);
                    if (result != null) {
                        log.debug("Serialized {} using {} strategy", 
                            event.getClass().getSimpleName(), serializer.getStrategyName());
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("Serializer {} failed for {}, trying next", 
                        serializer.getStrategyName(), event.getClass().getSimpleName(), e);
                }
            }
        }
        
        throw new EventSerializationException("No serializer could handle " +
            event.getClass().getSimpleName());
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> eventClass) {
        
        SerializationFormat format = FormatUtils.detectFormat(data);
        
        // Find appropriate deserializer based on format
        EventSerializer deserializer = findDeserializer(format);
        
        if (deserializer != null) {
            return deserializer.deserialize(data, eventClass);
        }
        
        // Fallback: try all deserializers
        for (EventSerializer serializer : serializers) {
            try {
                return serializer.deserialize(data, eventClass);
            } catch (Exception e) {
                log.debug("Deserializer {} failed for {}", serializer.getStrategyName(), 
                    eventClass.getSimpleName());
            }
        }
        
        throw new EventSerializationException("No deserializer could handle data for " + 
            eventClass.getSimpleName());
    }
    
    @Override
    public boolean canHandle(Object event) {
        return true; // Hybrid manager can handle anything
    }
    
    @Override
    public String getStrategyName() {
        return "HYBRID";
    }
    
    private EventSerializer findDeserializer(SerializationFormat format) {
        return serializers.stream()
            .filter(s -> s.getStrategyName().equals(format.name()) || 
                        (format == SerializationFormat.JSON_COMPRESSED && 
                         s.getStrategyName().equals(SerializationFormat.JSON_COMPRESSED.name())))
            .findFirst()
            .orElse(null);
    }

}
