package com.telco.platform.starter.lock;

import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockHandle;
import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LockAutoConfiguration}'s conditional-bean wiring (feature 17.2.2). Redisson's
 * single-server connection pool connects eagerly at {@code Redisson.create()}, so building the
 * {@link RedissonClient} bean here needs a reachable Redis - hence extending
 * {@link RedisContainerSupport} rather than pointing at the unset localhost default.
 */
class LockAutoConfigurationTest extends RedisContainerSupport {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class))
            .withPropertyValues("telco.platform.lock.redis.address=" + redisAddress());

    @Test
    void enabledByDefaultExposesRedissonClientAndDistributedLockBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedissonClient.class);
            assertThat(context).hasSingleBean(DistributedLock.class);
            assertThat(context).hasSingleBean(LockProperties.class);
        });
    }

    @Test
    void disabledPropertySuppressesAllBeans() {
        contextRunner
                .withPropertyValues("telco.platform.lock.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedissonClient.class);
                    assertThat(context).doesNotHaveBean(DistributedLock.class);
                });
    }

    @Test
    void conditionalOnMissingBeanAllowsOverridingDistributedLock() {
        contextRunner
                .withUserConfiguration(CustomLockConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context.getBean(DistributedLock.class)).isSameAs(CustomLockConfig.CUSTOM_LOCK);
                });
    }

    @Configuration
    static class CustomLockConfig {

        static final DistributedLock CUSTOM_LOCK = new DistributedLock() {
            @Override
            public LockHandle acquire(String key, Duration leaseTime) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T withLock(String key, Duration leaseTime, Callable<T> action) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void withLock(String key, Duration leaseTime, Runnable action) {
                throw new UnsupportedOperationException();
            }
        };

        @Bean
        DistributedLock distributedLock() {
            return CUSTOM_LOCK;
        }
    }
}
