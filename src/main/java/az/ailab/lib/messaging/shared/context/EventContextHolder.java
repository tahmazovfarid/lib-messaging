package az.ailab.lib.messaging.shared.context;

import az.ailab.lib.messaging.core.vo.EventProcessingContext;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class EventContextHolder {

    // Thread-local context for current processing event
    private static final ThreadLocal<EventProcessingContext> CURRENT_EVENT = new ThreadLocal<>();

    // Global cache for event metadata lookup
    private static final Map<String, EventProcessingContext> GLOBAL_METADATA_CACHE = new ConcurrentHashMap<>();


    /**
     * Set current event metadata for this thread
     */
    public static void setCurrentEvent(@NonNull EventProcessingContext eventProcessingContext) {
        CURRENT_EVENT.set(eventProcessingContext);
        log.debug("Set current event context: {}", eventProcessingContext.name());
    }

    /**
     * Get current event metadata for this thread
     */
    public static Optional<EventProcessingContext> getCurrentEvent() {
        return Optional.ofNullable(CURRENT_EVENT.get());
    }

    /**
     * Get current event metadata or throw exception
     */
    public static EventProcessingContext requireCurrentEvent() {
        return getCurrentEvent()
                .orElseThrow(() -> new IllegalStateException("No event context set for current thread"));
    }

    /**
     * Clear current thread context
     */
    public static void clearCurrentEvent() {
        EventProcessingContext removed = CURRENT_EVENT.get();
        CURRENT_EVENT.remove();

        if (removed != null) {
            log.debug("Cleared event context: {}", removed.name());
        }
    }

    /**
     * Cache event metadata globally for lookup
     */
    public static void cacheEventMetadata(@NonNull EventProcessingContext eventProcessingContext) {
        EventProcessingContext existing = GLOBAL_METADATA_CACHE.put(eventProcessingContext.name(), eventProcessingContext);

        if (existing == null) {
            log.info("Cached new event metadata: {}", eventProcessingContext.name());
        } else {
            log.debug("Updated cached event metadata: {}", eventProcessingContext.name());
        }
    }

    /**
     * Get cached event metadata by name
     */
    public static Optional<EventProcessingContext> getCachedMetadata(String eventName) {
        if (!StringUtils.hasText(eventName)) {
            return Optional.empty();
        }

        return Optional.ofNullable(GLOBAL_METADATA_CACHE.get(eventName));
    }

    /**
     * Get cached event metadata or throw exception
     */
    public static EventProcessingContext requireCachedMetadata(String eventName) {
        return getCachedMetadata(eventName)
                .orElseThrow(() -> new IllegalArgumentException("No cached metadata found for event: " + eventName));
    }

    /**
     * Check if event metadata is cached
     */
    public static boolean isEventCached(String eventName) {
        return eventName != null && GLOBAL_METADATA_CACHE.containsKey(eventName);
    }

    /**
     * Get all cached event names
     */
    public static Set<String> getCachedEventNames() {
        return Set.copyOf(GLOBAL_METADATA_CACHE.keySet());
    }

    /**
     * Get cache size
     */
    public static int getCacheSize() {
        return GLOBAL_METADATA_CACHE.size();
    }

    /**
     * Clear specific event from cache
     */
    public static boolean removeCachedEvent(String eventName) {
        if (eventName == null) {
            return false;
        }

        EventProcessingContext removed = GLOBAL_METADATA_CACHE.remove(eventName);
        if (removed != null) {
            log.info("Removed cached event metadata: {}", eventName);
            return true;
        }
        return false;
    }

    /**
     * Clear all cached metadata
     */
    public static void clearCache() {
        int size = GLOBAL_METADATA_CACHE.size();
        GLOBAL_METADATA_CACHE.clear();
        log.info("Cleared {} cached event metadata entries", size);
    }

    /**
     * Get context statistics
     */
    public static Map<String, Object> getContextStats() {
        return Map.of(
                "cachedEventsCount", GLOBAL_METADATA_CACHE.size(),
                "cachedEventNames", getCachedEventNames(),
                "hasCurrentThreadContext", getCurrentEvent().isPresent(),
                "currentThreadEvent", getCurrentEvent().map(EventProcessingContext::name).orElse("none")
        );
    }

    /**
     * Log current context state
     */
    public static void logContextState() {
        log.info("EventMetadataContext state:");
        log.info("  Cached events: {}", GLOBAL_METADATA_CACHE.size());
        log.info("  Current thread context: {}",
                getCurrentEvent().map(EventProcessingContext::name).orElse("none"));

        if (!GLOBAL_METADATA_CACHE.isEmpty()) {
            log.debug("  Cached event names: {}", getCachedEventNames());
        }
    }

    public void cleanup() {
        log.info("Cleaning up EventMetadataContext...");
        clearCache();
        clearCurrentEvent();
    }

}
