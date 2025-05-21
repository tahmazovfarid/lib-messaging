package az.ailab.lib.messaging.config;

import az.ailab.lib.messaging.config.properties.RedisProperties;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class responsible for setting up the Redisson client instance
 * used for Redis operations within the messaging library.
 * <p>
 * This class reads Redis connection properties from {@link RedisProperties}
 * and initializes a {@link RedissonClient} bean accordingly.
 *
 * @since 1.2.1
 * @author tahmazovfarid
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@Slf4j
@RequiredArgsConstructor
public class RedissonConfiguration {

    private final RedisProperties redisProperties;

    /**
     * Validates the required Redis configuration properties on application startup.
     * <p>
     * If either the host or port is missing, the application will terminate with
     * an explanatory error message.
     */
    @PostConstruct
    public void init() {
        if (StringUtils.isBlank(redisProperties.getHost()) || redisProperties.getPort() == null) {
            log.error("""
                    Redis host or port is not configured!
                    ➤ Please check your application configuration:
                      - Property 'spring.data.redis.host' must not be blank.
                      - Property 'spring.data.redis.port' must be defined.
                    ➤ Example:
                      spring:
                        data
                          redis:
                            host: localhost
                            port: 6379
                    
                    The application cannot proceed without Redis configuration.""");
            System.exit(-1);
        }
    }

    /**
     * Creates and configures a {@link RedissonClient} instance for Redis communication.
     * <p>
     * This configuration supports optional username and password authentication,
     * enabling compatibility with Redis ACL (Access Control List) setups.
     *
     * @return a configured Redisson client instance
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(10);

        if (StringUtils.isNotBlank(redisProperties.getUsername())) {
            singleServerConfig.setUsername(redisProperties.getUsername());
        }

        if (StringUtils.isNotBlank(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }

}
