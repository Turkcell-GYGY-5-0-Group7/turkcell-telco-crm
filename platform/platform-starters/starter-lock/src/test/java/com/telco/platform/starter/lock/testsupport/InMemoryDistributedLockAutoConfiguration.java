package com.telco.platform.starter.lock.testsupport;

import com.telco.platform.lock.DistributedLock;
import com.telco.platform.lock.LockHandle;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A trivial in-JVM {@link DistributedLock}, packaged in {@code starter-lock}'s test-jar (alongside
 * {@link RedisContainerSupport}) for consuming services' pre-existing test suites.
 *
 * <p>Once a service adds {@code starter-lock} as a real dependency (features 17.3/17.4), any
 * {@code @Component} in that service requiring a {@link DistributedLock} makes the bean mandatory in
 * every Spring context - including every pre-existing test that has nothing to do with locking.
 * Those tests don't need genuine cross-instance (Redis-backed) coordination, only a working bean, so
 * a service sets {@code telco.platform.lock.enabled=false} in its shared test profile (skipping the
 * real Redisson autoconfiguration, which connects eagerly and would otherwise require a live Redis in
 * every such test) and this configuration supplies a real, functioning, single-JVM substitute
 * instead. Tests that need actual cross-instance coordination (e.g. a {@code *ConcurrencyIT}) set
 * {@code telco.platform.lock.enabled=true} and point at a real Testcontainers Redis via
 * {@link RedisContainerSupport} - this configuration then steps aside via
 * {@link ConditionalOnMissingBean}, and the real Redisson-backed bean is used.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "telco.platform.lock", name = "enabled", havingValue = "false")
public class InMemoryDistributedLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributedLock distributedLock() {
        return new InMemoryDistributedLock();
    }

    private static final class InMemoryDistributedLock implements DistributedLock {

        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public LockHandle acquire(String key, Duration leaseTime) {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            lock.lock();
            return new InMemoryLockHandle(key, lock);
        }

        @Override
        public <T> T withLock(String key, Duration leaseTime, Callable<T> action) {
            try (LockHandle handle = acquire(key, leaseTime)) {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Action under lock [" + key + "] failed", e);
            }
        }

        @Override
        public void withLock(String key, Duration leaseTime, Runnable action) {
            try (LockHandle handle = acquire(key, leaseTime)) {
                action.run();
            }
        }
    }

    private record InMemoryLockHandle(String key, ReentrantLock lock) implements LockHandle {

        @Override
        public void release() {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
