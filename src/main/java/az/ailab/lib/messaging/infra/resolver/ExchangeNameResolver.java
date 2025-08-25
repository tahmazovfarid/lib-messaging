package az.ailab.lib.messaging.infra.resolver;

import io.micrometer.common.util.StringUtils;

/**
 * Utility class for resolving exchange names based on provided parameters and configuration.
 *
 * <p>This class is responsible for constructing exchange names according to naming conventions
 * and ensuring they are properly formatted.</p>
 */
public class ExchangeNameResolver {

    /**
     * Default constructor.
     */
    public ExchangeNameResolver() {
        // No initialization needed
    }

    /**
     * Resolves the exchange name based on the provided exchange name.
     *
     * <p>This method ensures that the exchange name is valid and properly formatted.</p>
     *
     * @param exchange the exchange name to resolve
     * @return the resolved exchange name
     * @throws IllegalArgumentException if the exchange name is invalid
     */
    public String resolveExchangeName(final String exchange) {
        if (StringUtils.isBlank(exchange)) {
            throw new IllegalArgumentException("Exchange name must not be empty");
        }

        return exchange;
    }

    /**
     * Resolves a dead letter exchange name for the given exchange name.
     *
     * @param exchange                 the original exchange name
     * @param deadLetterExchangeSuffix the suffix to append for dead letter exchanges
     * @return the dead letter exchange name
     */
    public String resolveDeadLetterExchangeName(final String exchange, final String deadLetterExchangeSuffix) {
        final String resolvedExchangeName = resolveExchangeName(exchange);
        return resolvedExchangeName + deadLetterExchangeSuffix;
    }

}