package com.telco.platform.starter.lock.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable Testcontainers Redis fixture (feature 17.2.1), packaged as this module's test-jar so
 * distributed-lock consumers' concurrency tests (subscription-service 17.3, billing-service 17.4)
 * can depend on it in test scope instead of duplicating Testcontainers Redis setup - mirroring the
 * {@code platform-event-contracts} test-jar precedent.
 *
 * <p>Extend this class and call {@link #redisAddress()} to wire
 * {@code telco.platform.lock.redis.address} (typically via {@code @DynamicPropertySource}). The
 * container is a singleton started once per JVM and shared across every subclass's test methods -
 * a fresh container per test class is unnecessary overhead for a stateless coordination primitive
 * (the standard Testcontainers "singleton container" pattern; the JVM-exit Ryuk reaper cleans it up,
 * no explicit {@code stop()} is called).
 */
public abstract class RedisContainerSupport {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    /** The running container's {@code redis://host:port} address, for Redisson/Spring config. */
    protected static String redisAddress() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }
}
