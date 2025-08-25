package az.ailab.lib.messaging.core.vo;

import az.ailab.lib.messaging.infra.error.EventExtractionException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

public record FieldMetadata(Field field,
                            String dependencyName,
                            Set<String> skipIfIn,
                            boolean optional,
                            boolean isAggregateId) {

    public Object getValue(Object event) {
        try {
            return field.get(event);
        } catch (IllegalAccessException e) {
            throw new EventExtractionException("Failed to access field: " + field.getName(), e);
        }
    }

    public static FieldMetadata initializeAggregateId(Field field) {
        return new FieldMetadata(field, null, Collections.emptySet(), false, true);
    }

    public static FieldMetadata initializeDependency(Field field, String name, Set<String> skipIfIn, boolean optional) {
        return new FieldMetadata(field, name, skipIfIn, optional, false);
    }

}