package az.ailab.lib.messaging.outbox.properties;

import az.ailab.lib.messaging.outbox.core.AbstractOutboxService;
import az.ailab.lib.messaging.outbox.model.entity.BaseOutboxEvent;
import az.ailab.lib.messaging.outbox.core.EventProcessor;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the outbox pattern implementation.
 * <p>
 * This class enables configuring multiple domain-specific outbox settings through standard
 * Spring application properties. The outbox pattern ensures reliable event publishing by
 * storing events in a database before asynchronously processing them.
 * </p>
 * <p>
 * Configuration is organized into a default set of properties that apply to all domains,
 * with the ability to override these settings on a per-domain basis. Each domain can
 * have its own processing interval, retry policy, retention period, and other settings.
 * </p>
 * <p><strong>Usage example in application.yml:</strong>
 * <pre>
 * outbox:
 *   defaults:
 *     processing-interval: 5000 # 5 seconds
 *     retry-interval: 60000 # 1 minute
 *     max-retries: 5
 *     cleanup-cron: "0 0 2 * * *" # 2:00 AM every day
 *     retention-days: 7 # 7 days
 *     processing-enabled: true
 *   domains:
 *     user:
 *       processing-interval: 3000
 *       retry-interval: 30000
 *     order:
 *       processing-interval: 1000
 *       retention-days: 30
 *     payment:
 *       processing-enabled: false
 * </pre></p>
 * 
 * @see OutboxDomainConfig for detailed configuration options for each domain
 * @see AbstractOutboxService for the service that uses these properties
 * @see BaseOutboxEvent for the base entity implementation
 * @see EventProcessor for event processing implementation
 *
 * @author tahmazovfarid
 */
@Data
@Component
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /**
     * Default configuration settings applied to all domains.
     * <p>
     * These settings will be used if domain-specific configuration is not provided for a particular domain.
     * By configuring reasonable defaults here, you can minimize repetition in your configuration files.
     * </p>
     */
    private OutboxDomainConfig defaults = new OutboxDomainConfig();

    /**
     * Domain-specific configurations mapped by domain name.
     * <p>
     * Each entry in this map represents a specific domain (e.g., "user", "order", "payment")
     * with its own configuration settings. These settings override the default configuration
     * for the specific domain.
     * </p>
     * <p>
     * The map key should match the value returned by {@code getDomainName()} in your outbox service
     * implementation.
     * </p>
     */
    private Map<String, OutboxDomainConfig> domains = new HashMap<>();

    /**
     * Retrieves configuration for a specific domain, falling back to default configuration if needed.
     * <p>
     * This method provides a convenient way to access domain-specific configuration, with automatic
     * fallback to the default configuration if no domain-specific settings exist.
     * </p>
     *
     * @param domain The domain name to get configuration for
     * @return Domain configuration for the specified domain, or default configuration if the domain
     *         is not specifically configured
     */
    public OutboxDomainConfig forDomain(String domain) {
        return domains.getOrDefault(domain, defaults);
    }

    /**
     * Configuration for a specific domain with various parameters to control outbox behavior.
     * <p>
     * Each domain can have its own settings for processing frequency, retry behavior, cleanup policies, etc.
     * </p>
     */
    @Data
    public static class OutboxDomainConfig {
        
        /**
         * Interval in milliseconds for processing pending events.
         * <p>
         * This controls how frequently the system checks for and processes new outbox events.
         * Lower values result in quicker processing but may increase database load.
         * </p>
         * Default: 5000 (5 seconds)
         */
        private int processingInterval = 5000;

        /**
         * Interval in milliseconds for retrying failed events.
         * <p>
         * This controls how frequently the system attempts to reprocess events that previously failed.
         * </p>
         * Default: 60000 (1 minute)
         */
        private int retryInterval = 60000;

        /**
         * Maximum number of retry attempts for failed events.
         * <p>
         * After this many failed attempts, the event will remain in the FAILED state and will
         * not be retried automatically.
         * </p>
         * Default: 5
         */
        private int maxRetries = 5;

        /**
         * Cron expression for the cleanup job that removes old processed events.
         * <p>
         * Uses standard Spring cron syntax.
         * </p>
         * Default: "0 0 2 * * *" (2:00 AM every day)
         */
        private String cleanupCron = "0 0 2 * * *";

        /**
         * Whether to enable event processing for this domain.
         * <p>
         * This allows temporarily disabling event processing for a specific domain
         * without changing any code (e.g., during maintenance windows).
         * </p>
         * Default: false
         */
        private boolean processingEnabled = false;

        /**
         * Number of days to keep processed events before cleanup.
         * <p>
         * Events that have been successfully processed will be kept for this many days
         * before being deleted by the cleanup job.
         * </p>
         * Default: 7
         */
        private int retentionDays = 7;

    }

}