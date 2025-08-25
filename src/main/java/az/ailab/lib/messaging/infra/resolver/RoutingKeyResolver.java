package az.ailab.lib.messaging.infra.resolver;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility class for resolving routing keys used in message routing.
 *
 * <p>This class is responsible for resolving and normalizing routing keys
 * to ensure proper message routing in the message broker.</p>
 */
public class RoutingKeyResolver {

    /**
     * Default constructor.
     */
    public RoutingKeyResolver() {
        // No initialization needed
    }

    /**
     * Resolves the routing key based on the provided parameters.
     *
     * @param routingKey the base routing key to resolve
     * @return the resolved routing key
     */
    public String resolveRoutingKey(final String routingKey) {
        if (routingKey == null || routingKey.isEmpty()) {
            return "#"; // Default routing key to catch all messages
        }

        return routingKey;
    }

    /**
     * Combines multiple routing key parts into a single routing key.
     *
     * @param basePart        the base part of the routing key
     * @param additionalParts additional parts to append to the routing key
     * @return the combined routing key
     */
    public String combineRoutingKeyParts(final String basePart, final String... additionalParts) {
        Objects.requireNonNull(basePart, "Base routing key part must not be null");

        if (additionalParts == null || additionalParts.length == 0) {
            return basePart;
        }

        final StringBuilder builder = new StringBuilder(basePart);

        Arrays.stream(additionalParts)
                .filter(Objects::nonNull)
                .filter(part -> !part.isEmpty())
                .forEach(part -> builder.append(".").append(part));

        return builder.toString();
    }

}