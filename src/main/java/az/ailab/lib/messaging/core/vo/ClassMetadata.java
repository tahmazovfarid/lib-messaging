package az.ailab.lib.messaging.core.vo;

import java.util.List;

public record ClassMetadata(List<FieldMetadata> dependencyFields,
                            FieldMetadata aggregateIdField,
                            String eventType) {

    public boolean hasAggregateId() {
        return aggregateIdField != null;
    }

    public boolean hasDependencies() {
        return !dependencyFields.isEmpty();
    }

}