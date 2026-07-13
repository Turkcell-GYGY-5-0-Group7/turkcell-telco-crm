package com.telco.platform.starter.lock;

import com.telco.platform.lock.DistributedLock;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configures a Redisson-backed {@link DistributedLock} (ADR-024). Active when
 * {@code telco.platform.lock.enabled} is not {@code false} and Redisson is on the classpath -
 * strictly opt-in, matching ADR-018's starter-only consumption model (no service depends on this
 * unless it adds {@code starter-lock} itself).
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "telco.platform.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LockProperties.class)
public class LockAutoConfiguration {

    /**
     * Builds the {@link RedissonClient} from {@code telco.platform.lock.redis.address}, falling back
     * to {@code spring.data.redis.host}/{@code port} when unset (ADR-024 Section 2's property table),
     * and applies {@code telco.platform.lock.watchdog-timeout} as Redisson's internal
     * lock-watchdog-timeout used for watchdog-managed leases (ADR-024 Section 4).
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(LockProperties properties, Environment environment) {
        Config config = new Config();
        config.setLockWatchdogTimeout(properties.getWatchdogTimeout().toMillis());
        config.useSingleServer().setAddress(resolveAddress(properties, environment));
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock(RedissonClient redissonClient, LockProperties properties) {
        return new RedissonDistributedLock(redissonClient, properties.getWaitTime());
    }

    private static String resolveAddress(LockProperties properties, Environment environment) {
        String address = properties.getRedis().getAddress();
        if (address != null && !address.isBlank()) {
            return address;
        }
        String host = environment.getProperty("spring.data.redis.host", "localhost");
        String port = environment.getProperty("spring.data.redis.port", "6379");
        return "redis://" + host + ":" + port;
    }
}
