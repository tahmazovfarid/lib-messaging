package az.ailab.lib.messaging.core.domain;

import java.util.Set;
import lombok.Builder;

@Builder
public record EventDependency(String name,
                              String aggregateId,
                              Set<String> skipAggregates,
                              boolean optional,
                              Event event) {

    public String getId() {
        return name + ":" + aggregateId;
    }

    public boolean shouldProcess() {
        return skipAggregates == null || !skipAggregates.contains(aggregateId);
    }

}