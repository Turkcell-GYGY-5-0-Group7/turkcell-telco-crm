package com.telco.platform.lock;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Cross-instance mutual-exclusion port (ADR-024). Coordinates a critical section across JVM
 * instances (multiple pods, or two different services), as opposed to within a single JVM
 * ({@code synchronized}) or within a single Postgres transaction ({@code SELECT ... FOR UPDATE}).
 *
 * <p>{@code leaseTime == null} requests a watchdog-managed lease: the implementation renews the
 * lock automatically for as long as the holder is alive, and it self-heals if the holder crashes.
 * A non-null {@code leaseTime} requests an explicit lease that hard-expires at exactly that
 * duration regardless of holder liveness. Callers choose per call site (ADR-024 Section 4).
 *
 * <p>Implementations fail CLOSED: if the lock cannot be acquired (infrastructure unavailable, or
 * the wait-time budget expires), the guarded action does not run (ADR-024 Section 5).
 */
public interface DistributedLock {

    /**
     * Acquires the lock, blocking up to the implementation's configured wait-time budget.
     *
     * @return a handle to release the lock via try-with-resources
     */
    LockHandle acquire(String key, Duration leaseTime);

    /** Acquires the lock, runs {@code action}, and releases the lock, returning its result. */
    <T> T withLock(String key, Duration leaseTime, Callable<T> action);

    /** Acquires the lock, runs {@code action}, and releases the lock. */
    void withLock(String key, Duration leaseTime, Runnable action);
}
