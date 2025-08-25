package az.ailab.lib.messaging.infra.extractor;

import az.ailab.lib.messaging.core.annotation.AggregateId;
import az.ailab.lib.messaging.core.annotation.DependsOnEvent;
import az.ailab.lib.messaging.core.domain.EventDependency;
import az.ailab.lib.messaging.core.vo.ClassMetadata;
import az.ailab.lib.messaging.core.vo.FieldMetadata;
import az.ailab.lib.messaging.infra.error.EventExtractionException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventMetaDataExtractor {

    private final Map<Class<?>, ClassMetadata> classMetadataCache = new ConcurrentHashMap<>();

    /**
     * Fast aggregate ID extraction
     */
    public String extractAggregateId(@NonNull Object event) {
        ClassMetadata classMetadata = getClassMetadata(event.getClass());
        return extractAggregateId(event, classMetadata);
    }

    /**
     * Fast dependencies extraction
     */
    public Set<EventDependency> extractDependencies(@NonNull Object event) {
        ClassMetadata classMetadata = getClassMetadata(event.getClass());
        return extractDependencies(event, classMetadata);
    }

    private ClassMetadata getClassMetadata(Class<?> eventClass) {
        ClassMetadata metadata = classMetadataCache.get(eventClass);

        if (metadata != null) {
            return metadata;
        }

        return classMetadataCache.computeIfAbsent(eventClass, this::buildClassMetadata);
    }

    private ClassMetadata buildClassMetadata(Class<?> eventClass) {
        log.debug("Building metadata cache for class: {}", eventClass.getSimpleName());

        List<FieldMetadata> dependencyFields = new ArrayList<>();
        FieldMetadata aggregateIdField = null;

        // Scan all fields including inherited ones
        Field[] fields = getAllFields(eventClass);

        for (Field field : fields) {
            field.setAccessible(true); // Make accessible once

            // Check for @AggregateId
            if (field.isAnnotationPresent(AggregateId.class)) {
                if (aggregateIdField != null) {
                    throw new EventExtractionException(
                            "Multiple @AggregateId annotations found in " + eventClass.getSimpleName());
                }
                aggregateIdField = FieldMetadata.initializeAggregateId(field);
            }

            // Check for @DependsOnEvent
            DependsOnEvent depAnnotation = field.getAnnotation(DependsOnEvent.class);
            if (depAnnotation != null) {
                Set<String> skipIfIn = Set.of(depAnnotation.skipIfIn());
                dependencyFields.add(
                        FieldMetadata.initializeDependency(field, depAnnotation.name(), skipIfIn, depAnnotation.optional())
                );
            }
        }

        ClassMetadata metadata = new ClassMetadata(
                Collections.unmodifiableList(dependencyFields),
                aggregateIdField,
                eventClass.getSimpleName()
        );

        log.info("Cached metadata for {}: {} dependencies, aggregate ID: {}",
                eventClass.getSimpleName(),
                dependencyFields.size(),
                aggregateIdField != null);

        return metadata;
    }

    private Field[] getAllFields(Class<?> clazz) {
        List<Field> allFields = new ArrayList<>();

        // Get fields from current class and all superclasses
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            allFields.addAll(Arrays.asList(fields));
            currentClass = currentClass.getSuperclass();
        }

        return allFields.toArray(new Field[0]);
    }

    private String extractAggregateId(Object event, ClassMetadata classMetadata) {
        if (!classMetadata.hasAggregateId()) {
            return null;
        }

        Object value = classMetadata.aggregateIdField().getValue(event);
        return value != null ? value.toString() : null;
    }

    private Set<EventDependency> extractDependencies(Object event, ClassMetadata classMetadata) {
        if (!classMetadata.hasDependencies()) {
            return Collections.emptySet();
        }

        Set<EventDependency> dependencies = new HashSet<>();

        for (FieldMetadata fieldMetadata : classMetadata.dependencyFields()) {
            Object value = fieldMetadata.getValue(event);

            if (value == null) {
                if (!fieldMetadata.optional()) {
                    log.warn("Non-optional dependency field '{}' is null in {}",
                            fieldMetadata.field().getName(),
                            classMetadata.eventType());
                }
                continue;
            }

            String dependedAggregateId = value.toString();

            EventDependency dependency = EventDependency.builder()
                    .name(fieldMetadata.dependencyName())
                    .aggregateId(dependedAggregateId)
                    .skipAggregates(fieldMetadata.skipIfIn())
                    .optional(fieldMetadata.optional())
                    .build();

            dependencies.add(dependency);
        }

        return dependencies;
    }

}
