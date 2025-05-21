package az.ailab.lib.messaging.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.data.redis")
@Getter
@Setter
public class RedisProperties {

    private String host;

    private Integer port;

    private String username;

    private String password;

}
