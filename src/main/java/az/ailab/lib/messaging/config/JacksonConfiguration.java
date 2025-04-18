package az.ailab.lib.messaging.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides standardized configuration for Jackson's ObjectMapper within a Spring application.
 *
 * <p>This configuration class creates a consistent ObjectMapper bean that can be used
 * throughout the application for all JSON serialization and deserialization needs.
 * It ensures that all Jackson-related operations follow the same rules, especially
 * important in applications that process dates or handle complex object hierarchies.</p>
 *
 * <p>The configuration applies the following settings to the ObjectMapper:</p>
 * <ul>
 *   <li>Adds support for Java 8 date/time types (LocalDate, LocalDateTime, etc.)
 *       through the JavaTimeModule</li>
 *   <li>Configures lenient deserialization by ignoring unknown JSON properties,
 *       making the application more robust when handling external APIs</li>
 *   <li>Ensures dates are serialized in ISO-8601 format rather than as timestamps,
 *       improving human readability and interoperability</li>
 *   <li>Sets a standardized date format with properly formatted time zones that include colons
 *       (e.g., "2023-04-15T14:30:00+02:00" instead of "2023-04-15T14:30:00+0200"),
 *       ensuring compliance with ISO-8601 and improving interoperability with other systems</li>
 * </ul>
 * <p>To use this configuration, either import it directly or prefer using the
 * {@code @EnableJacksonConfiguration} annotation in your application.</p>
 *
 * @see ObjectMapper
 * @see JavaTimeModule
 * @see StdDateFormat
 */
@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setDateFormat(new StdDateFormat().withColonInTimeZone(true))
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

}
