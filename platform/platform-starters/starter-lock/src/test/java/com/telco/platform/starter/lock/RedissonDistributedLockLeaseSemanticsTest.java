package com.telco.platform.starter.lock;

import com.telco.platform.lock.LockHandle;
import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the two lease modes {@link RedissonDistributedLock} exposes per ADR-024 Section 4, against
 * a real Redis: a watchdog-managed lease ({@code leaseTime == null}) survives a holder that runs
 * past the watchdog-timeout, while an explicit-{@link Duration} lease hard-expires on schedule
 * regardless of holder liveness.
 */
class RedissonDistributedLockLeaseSemanticsTest extends RedisContainerSupport {

    private RedissonClient redissonClient;

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void watchdogManagedLeaseSurvivesPastTheWatchdogTimeout() throws InterruptedException {
        // A short watchdog-timeout keeps this test fast: the holder deliberately runs long enough
        // (well past one watchdog interval) to prove Redisson's internal renewal, not this test's
        // patience, is what keeps the lock alive.
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress());
        config.setLockWatchdogTimeout(2000);
        redissonClient = Redisson.create(config);
        RedissonDistributedLock lock = new RedissonDistributedLock(redissonClient, Duration.ofMillis(300));

        String key = "test:watchdog:" + UUID.randomUUID();
        CountDownLatch holderAcquired = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicBoolean holderCompleted = new AtomicBoolean(false);

        Thread holder = new Thread(() -> lock.withLock(key, null, () -> {
            holderAcquired.countDown();
            try {
                // Hold well past the 2s watchdog-timeout (3x the interval) so at least one internal
                // renewal must have happened for the lock to still be held.
                releaseHolder.await(6, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            holderCompleted.set(true);
        }));
        holder.setDaemon(true);
        holder.start();

        assertThat(holderAcquired.await(5, TimeUnit.SECONDS)).isTrue();
        // Give the watchdog-timeout window a chance to elapse before the competing attempt.
        Thread.sleep(2500);

        // The holder is still working, past the nominal watchdog-timeout: a competing acquire with a
        // short wait-time budget must fail (the watchdog kept renewing the original holder's lease).
        assertThat(tryAcquireFailsClosed(redissonClient, key)).isTrue();

        releaseHolder.countDown();
        holder.join(5000);
        assertThat(holderCompleted.get()).isTrue();
    }

    @Test
    void explicitLeaseHardExpiresRegardlessOfHolderLiveness() throws InterruptedException {
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress());
        redissonClient = Redisson.create(config);
        RedissonDistributedLock lock = new RedissonDistributedLock(redissonClient, Duration.ofSeconds(8));

        String key = "test:explicit-lease:" + UUID.randomUUID();
        CountDownLatch holderAcquired = new CountDownLatch(1);

        // Holder acquires a short, explicit 2s lease and then never releases it (no close()/unlock())
        // - simulating a stalled or crashed holder. The lease must hard-expire on its own.
        Thread holder = new Thread(() -> {
            LockHandle handle = lock.acquire(key, Duration.ofSeconds(2));
            holderAcquired.countDown();
            try {
                Thread.sleep(8000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Deliberately no handle.close() / handle.release() call.
        });
        holder.setDaemon(true);
        holder.start();

        assertThat(holderAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        // A second acquirer succeeds once the 2s explicit lease elapses, well before the first
        // holder's 8s sleep finishes and despite it never releasing.
        LockHandle secondHandle = lock.acquire(key, Duration.ofSeconds(5));
        try {
            assertThat(secondHandle.key()).isEqualTo(key);
        } finally {
            secondHandle.close();
        }
    }

    /** @return true if a competing, short-wait acquire attempt on {@code key} fails to acquire. */
    private static boolean tryAcquireFailsClosed(RedissonClient client, String key) {
        RedissonDistributedLock competing = new RedissonDistributedLock(client, Duration.ofMillis(300));
        try {
            competing.acquire(key, Duration.ofSeconds(1)).close();
            return false;
        } catch (com.telco.platform.common.exception.DependencyFailureException e) {
            return true;
        }
    }
}
