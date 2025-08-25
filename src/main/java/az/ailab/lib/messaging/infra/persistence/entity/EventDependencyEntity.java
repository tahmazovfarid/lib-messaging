package az.ailab.lib.messaging.infra.persistence.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_dependencies")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDependencyEntity {

    @Id
    private String id;

    private String name; // dependency name

    private String aggregateId; // dependency key which contains main payload inside

    @ElementCollection
    private Set<String> skipAggregates; // these aggregates inserted with liquibase

    private boolean optional;

    @ManyToOne
    private EventEntity event;

}