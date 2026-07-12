package com.telco.platform.starter.lock;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the ADR-024 Section 5 fail-closed contract against a real Redis client whose connection has
 * gone away: {@link RedissonDistributedLock#withLock} throws {@link DependencyFailureException} and
 * the guarded action is never invoked - not silently proceeding uncoordinated.
 *
 * <p>Simulates the outage by shutting down this test's own private {@link RedissonClient} after it
 * has connected successfully, rather than stopping the shared {@link RedisContainerSupport}
 * container (which other test classes in this suite depend on) or pointing at an address that is
 * unreachable from the start (Redisson's connection pool connects eagerly at {@code Redisson.create()}
 * and would fail there instead of at {@code acquire}/{@code withLock}, which is not what this test is
 * proving).
 */
class RedissonDistributedLockFailureModeTest extends RedisContainerSupport {

    @Test
    void redisOutageFailsClosedAndNeverRunsTheGuardedAction() {
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress());
        RedissonClient redissonClient = Redisson.create(config);
        redissonClient.shutdown();

        RedissonDistributedLock lock = new RedissonDistributedLock(redissonClient, Duration.ofMillis(500));
        AtomicInteger invocations = new AtomicInteger();
        String key = "test:outage:" + UUID.randomUUID();

        assertThatThrownBy(() -> lock.withLock(key, Duration.ofSeconds(5), (Runnable) invocations::incrementAndGet))
                .isInstanceOf(DependencyFailureException.class)
                .satisfies(ex -> {
                    DependencyFailureException dfe = (DependencyFailureException) ex;
                    assertThat(dfe.code().code()).isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.code());
                    assertThat(dfe.details()).containsEntry("lockKey", key);
                });

        assertThat(invocations.get()).isZero();
    }
}
