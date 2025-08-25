package az.ailab.lib.messaging.infra.persistence.mapper;

import az.ailab.lib.messaging.core.domain.Event;
import az.ailab.lib.messaging.core.domain.EventDependency;
import az.ailab.lib.messaging.infra.persistence.entity.EventDependencyEntity;
import az.ailab.lib.messaging.infra.persistence.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EventMapper {

    @Mapping(target = "dependencies", ignore = true)
    EventEntity map(Event event);

    Event map(EventEntity entity);

    @Mapping(target = "event", ignore = true)
    EventDependency map(EventDependencyEntity entity);

    @Mapping(source = "dependency.id", target = "id")
    @Mapping(source = "dependency.name", target = "name")
    @Mapping(source = "dependency.aggregateId", target = "aggregateId")
    @Mapping(source = "dependency.skipAggregates", target = "skipAggregates")
    @Mapping(source = "dependency.optional", target = "optional")
    @Mapping(source = "event", target = "event")
    EventDependencyEntity map(EventDependency dependency, Event event);

}
