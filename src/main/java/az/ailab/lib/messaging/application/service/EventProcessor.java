package az.ailab.lib.messaging.application.service;

/**
 * Interface for event processors that handle specific event types.
 * Implementations of this interface should process a specific type of event.
 *
 * @author tahmazovfarid
 */
public interface EventProcessor {
    
    /**
     * Checks if this processor can process the given event type
     *
     * @param eventType The type of event to check
     * @return true if this processor can handle the event type, false otherwise
     */
    boolean canProcess(String eventType);
    
    /**
     * Processes the event with the given payload
     *
     * @param payload The event payload in JSON format
     * @throws Exception If processing fails
     */
    void processEvent(String payload) throws Exception;

}