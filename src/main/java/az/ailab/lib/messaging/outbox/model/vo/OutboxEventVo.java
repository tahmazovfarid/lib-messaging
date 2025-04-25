package az.ailab.lib.messaging.outbox.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * Value Object (VO) for creating outbox events.
 * This provides a clean way to create new outbox events with the necessary data.
 *
 * @author tahmazovfarid
 */
@Data
@Builder
public class OutboxEventVo {
    
    /**
     * The aggregate ID (identifier of the domain object this event belongs to)
     */
    private final String aggregateId;
    
    /**
     * The event type (e.g., USER_PROFILE, ORDER_CREATED)
     */
    private final String eventType;
    
    /**
     * The event payload in JSON format
     */
    private final String payload;

}