package com.telco.platform.starter.lock;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.lock.LockHandle;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed {@link DistributedLock} (ADR-024). {@code leaseTime == null} uses Redisson's
 * watchdog-managed lease (auto-renewed while the holder is alive); a non-null {@code leaseTime}
 * uses an explicit hard-expiring lease (ADR-024 Section 4).
 *
 * <p>Fails closed: on connection failure, interruption, or exhausting the configured wait-time
 * budget without acquiring the lock, throws the platform's existing {@link DependencyFailureException}
 * (constructed with {@link LockErrorCode#LOCK_ACQUISITION_FAILED}) - the guarded action never runs
 * (ADR-024 Section 5).
 */
public class RedissonDistributedLock implements DistributedLock {

    private final RedissonClient redissonClient;
    private final Duration waitTime;

    public RedissonDistributedLock(RedissonClient redissonClient, Duration waitTime) {
        this.redissonClient = redissonClient;
        this.waitTime = waitTime;
    }

    @Override
    public LockHandle acquire(String key, Duration leaseTime) {
        RLock rLock = redissonClient.getLock(key);
        boolean acquired;
        try {
            acquired = leaseTime == null
                    ? rLock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS)
                    : rLock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw acquisitionFailed(key, e);
        } catch (RuntimeException e) {
            // Redisson surfaces connection failures/timeouts as unchecked exceptions
            // (org.redisson.client.RedisConnectionException / RedisTimeoutException and similar).
            throw acquisitionFailed(key, e);
        }
        if (!acquired) {
            throw acquisitionFailed(key, null);
        }
        return new RedissonLockHandle(key, rLock);
    }

    @Override
    public <T> T withLock(String key, Duration leaseTime, Callable<T> action) {
        try (LockHandle handle = acquire(key, leaseTime)) {
            return action.call();
        } catch (RuntimeException e) {
            // Includes DependencyFailureException from acquire() and any unchecked exception the
            // guarded action itself throws (e.g. a domain exception meant for GlobalExceptionHandler's
            // type-based dispatch, such as BusinessRuleException) - propagate unwrapped, exactly like
            // the Runnable overload below already does.
            throw e;
        } catch (Exception e) {
            // Callable<T> permits checked exceptions; Runnable does not, so only this overload needs
            // to translate one into something unchecked.
            throw new IllegalStateException("Action under lock [" + key + "] failed", e);
        }
    }

    @Override
    public void withLock(String key, Duration leaseTime, Runnable action) {
        try (LockHandle handle = acquire(key, leaseTime)) {
            action.run();
        }
    }

    private static DependencyFailureException acquisitionFailed(String key, Throwable cause) {
        return new DependencyFailureException(
                LockErrorCode.LOCK_ACQUISITION_FAILED,
                "Failed to acquire distributed lock [" + key + "]",
                Map.of("lockKey", key),
                cause);
    }
}
