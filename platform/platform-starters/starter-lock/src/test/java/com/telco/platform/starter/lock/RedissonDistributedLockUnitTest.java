package com.telco.platform.starter.lock;

import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.lock.LockErrorCode;
import com.telco.platform.lock.LockHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast, Docker-independent unit tests for {@link RedissonDistributedLock} against a mocked
 * {@link RLock}/{@link RedissonClient} (feature 17.2.2 code-review follow-up, 2026-07-12): the
 * lease-mode branching, fail-closed exception mapping, and exception-transparency contract are all
 * plain conditional/catch logic that does not need a real Redis to verify - the four Testcontainers
 * classes in this package prove the same guarantees end to end against a real Redis, but do not (and,
 * being environment-dependent, currently cannot in this sandbox - see {@code docs/tasks/lessons.md}
 * 2026-07-12) run on every build.
 */
@ExtendWith(MockitoExtension.class)
class RedissonDistributedLockUnitTest {

    private static final String KEY = "test:key";
    private static final Duration WAIT_TIME = Duration.ofSeconds(5);

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    private RedissonDistributedLock lock;

    @BeforeEach
    void setUp() {
        when(redissonClient.getLock(KEY)).thenReturn(rLock);
        lock = new RedissonDistributedLock(redissonClient, WAIT_TIME);
    }

    @Test
    void nullLeaseTimeUsesTheWatchdogManagedTwoArgTryLock() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);

        LockHandle handle = lock.acquire(KEY, null);

        assertThat(handle.key()).isEqualTo(KEY);
        verify(rLock, never()).tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void explicitLeaseTimeUsesTheThreeArgTryLock() throws InterruptedException {
        Duration lease = Duration.ofSeconds(2);
        when(rLock.tryLock(WAIT_TIME.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);

        lock.acquire(KEY, lease);

        verify(rLock).tryLock(WAIT_TIME.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void acquireFailsClosedWhenTryLockReturnsFalse() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> lock.acquire(KEY, null))
                .isInstanceOf(DependencyFailureException.class)
                .satisfies(ex -> assertThat(((DependencyFailureException) ex).code().code())
                        .isEqualTo(LockErrorCode.LOCK_ACQUISITION_FAILED.code()));
    }

    @Test
    void acquireFailsClosedAndPreservesInterruptStatusOnInterruptedException() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        try {
            assertThatThrownBy(() -> lock.acquire(KEY, null)).isInstanceOf(DependencyFailureException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag so it cannot leak into another test on this thread
        }
    }

    @Test
    void acquireFailsClosedOnAnyRedissonRuntimeException() throws InterruptedException {
        RuntimeException redissonFailure = new RuntimeException("connection refused");
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenThrow(redissonFailure);

        assertThatThrownBy(() -> lock.acquire(KEY, null))
                .isInstanceOf(DependencyFailureException.class)
                .hasCause(redissonFailure);
    }

    @Test
    void withLockCallablePropagatesRuntimeExceptionsFromTheActionUnwrapped() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        IllegalArgumentException domainFailure = new IllegalArgumentException("boom");
        Callable<String> action = () -> {
            throw domainFailure;
        };

        // Regression test for a code-review finding: this overload previously rewrapped every
        // exception (including domain RuntimeExceptions meant for GlobalExceptionHandler's
        // type-based dispatch) as IllegalStateException.
        assertThatThrownBy(() -> lock.withLock(KEY, null, action)).isSameAs(domainFailure);
    }

    @Test
    void withLockCallableWrapsCheckedExceptionsFromTheAction() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        Callable<String> action = () -> {
            throw new IOException("checked");
        };

        assertThatThrownBy(() -> lock.withLock(KEY, null, action))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void withLockRunnableReleasesTheLockAfterSuccess() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        AtomicInteger invocations = new AtomicInteger();

        lock.withLock(KEY, null, (Runnable) invocations::incrementAndGet);

        assertThat(invocations.get()).isEqualTo(1);
        verify(rLock).unlock();
    }

    @Test
    void withLockNeverInvokesTheGuardedActionWhenAcquisitionFails() throws InterruptedException {
        when(rLock.tryLock(WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)).thenReturn(false);
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> lock.withLock(KEY, null, (Runnable) invocations::incrementAndGet))
                .isInstanceOf(DependencyFailureException.class);

        assertThat(invocations.get()).isZero();
        verify(rLock, never()).unlock();
    }
}
