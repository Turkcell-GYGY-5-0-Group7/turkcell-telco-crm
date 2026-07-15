package com.telco.subscription.infrastructure.scheduler;

import com.telco.platform.starter.lock.testsupport.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves exactly one replica performs a given tick's MSISDN-reservation sweep (feature 17.3.2,
 * Sprint 17 README exit criteria), against real Testcontainers Postgres + Redis - not a mocked
 * {@code DistributedLock}.
 *
 * <p>Simulates N&gt;=2 replicas racing by calling the same {@link MsisdnReservationExpiryReaper}
 * bean's package-private {@code tick()} from separate threads (Redisson's mutual exclusion is scoped
 * to the Redis key, not the calling Java object, so this is equivalent to independent replicas
 * sharing the same Redis). The robust, timing-independent invariant asserted is the DB end-state, not
 * which specific thread "won": exactly one {@code audit_log} row per originally-expired MSISDN (no
 * double release) and zero remaining expired {@code RESERVED} rows.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "telco.platform.lock.enabled=true",
                "telco.platform.lock.wait-time=2s",
                "telco.subscription.msisdn-reaper.enabled=true"
        })
@ActiveProfiles("test")
@Testcontainers
class MsisdnReservationExpiryReaperConcurrencyIT extends RedisContainerSupport {

    private static final int EXPIRED_COUNT = 50;
    private static final int CONCURRENT_REPLICAS = 3;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("telco.platform.lock.redis.address", MsisdnReservationExpiryReaperConcurrencyIT::redisAddress);
    }

    @Autowired
    private MsisdnReservationExpiryReaper reaper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");
        jdbc.update("""
                UPDATE msisdn_pool
                SET status = 'RESERVED', reserved_until = now() - interval '1 hour'
                WHERE msisdn IN (
                    SELECT msisdn FROM msisdn_pool WHERE status = 'FREE' ORDER BY msisdn LIMIT ?
                )
                """, EXPIRED_COUNT);
    }

    @Test
    void exactlyOneReplicaSweepsPerTickWithNoDoubleRelease() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REPLICAS);
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENT_REPLICAS);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REPLICAS; i++) {
                futures.add(executor.submit(() -> {
                    startTogether.await();
                    return reaper.tick();
                }));
            }
            for (Future<Integer> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        Long auditCount = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'MSISDN_RESERVATION_EXPIRED'", Long.class);
        assertThat(auditCount).isEqualTo((long) EXPIRED_COUNT);

        Long stillExpiredAndReserved = jdbc.queryForObject(
                "SELECT count(*) FROM msisdn_pool WHERE status = 'RESERVED' AND reserved_until < now()",
                Long.class);
        assertThat(stillExpiredAndReserved).isZero();
    }
}
