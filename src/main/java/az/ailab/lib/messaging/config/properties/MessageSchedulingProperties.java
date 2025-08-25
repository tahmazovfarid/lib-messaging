package az.ailab.lib.messaging.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for scheduling event replay and cleanup tasks.
 *
 * <p>Maps properties under the prefix
 * {@code spring.rabbitmq.config.message.scheduling} to structured POJOs.</p>
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code processing.fixed-delay-ms} – delay in milliseconds between
 *       consecutive invocations of the “process ready events” task.</li>
 *   <li>{@code cleanup.cron} – cron expression defining when to run
 *       the “cleanup completed events” task.</li>
 *   <li>{@code cleanup.older-than-days} – number of days; events older
 *       than this will be purged.</li>
 * </ul>
 *
 * <p>Example YAML configuration:</p>
 * <pre>{@code
 * spring:
 *   rabbitmq:
 *     config:
 *       message:
 *         scheduling:
 *           processing:
 *             fixed-delay-ms: 15000
 *           cleanup:
 *             cron: "0 0 2 * * ?"       # every night at 2 AM
 *             older-than-days: 7
 * }</pre>
 *
 * @author tahmazovfarid
 */
@ConfigurationProperties(prefix="spring.rabbitmq.config.message.scheduling")
@Getter
@Setter
public class MessageSchedulingProperties {

    private Processing processing = new Processing();
    private Cleanup cleanup = new Cleanup();

    @Getter
    @Setter
    public static class Processing {
        private long fixedDelayMs;
    }

    @Getter
    @Setter
    public static class Cleanup {
        private String cron;
        private int olderThanDays;
    }

}