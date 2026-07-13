package com.telco.platform.starter.lock;

import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link RedissonDistributedLock} mutual exclusion under real concurrent acquisition against
 * a real Redis (feature 17.2.2, ADR-024 Section 3) - not mocked Redisson behavior.
 */
class RedissonDistributedLockContentionTest extends RedisContainerSupport {

    private RedissonClient redissonClient;
    private RedissonDistributedLock lock;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress(redisAddress());
        redissonClient = Redisson.create(config);
        lock = new RedissonDistributedLock(redissonClient, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDown() {
        redissonClient.shutdown();
    }

    @Test
    void concurrentAcquisitionsOnTheSameKeySerialize() throws InterruptedException {
        String key = "test:contention:" + UUID.randomUUID();
        int threadCount = 8;
        int incrementsPerThread = 25;
        AtomicInteger counter = new AtomicInteger(0);
        AtomicBoolean insideCriticalSection = new AtomicBoolean(false);
        AtomicInteger overlapDetections = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch done = new CountDownLatch(threadCount);
        try {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < incrementsPerThread; i++) {
                            lock.withLock(key, Duration.ofSeconds(10), () -> {
                                // A genuine mutual-exclusion violation is caught definitively here:
                                // compareAndSet only ever sees `false` if no other thread is
                                // concurrently inside this same critical section.
                                if (!insideCriticalSection.compareAndSet(false, true)) {
                                    overlapDetections.incrementAndGet();
                                }
                                int current = counter.get();
                                sleepBriefly();
                                counter.set(current + 1);
                                insideCriticalSection.set(false);
                            });
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        assertThat(overlapDetections.get()).isZero();
        assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
