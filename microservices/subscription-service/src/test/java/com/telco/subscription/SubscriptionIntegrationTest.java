package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.command.TerminateSubscriptionCommand;
import com.telco.subscription.domain.MsisdnStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * Full-stack integration test for subscription-service (feature 9.2, ADR-013).
 *
 * <p>Boots the full Spring context against a Testcontainers Postgres with the real Mediator pipeline
 * (TransactionBehavior), Flyway schema + MSISDN seed, and the real JDBC-only {@code OutboxService}
 * (no Kafka) so outbox rows can be asserted directly on the {@code outbox_event} table.
 *
 * <p>Covers:
 * <ul>
 *   <li>(a) context boots and the Flyway migration applies (also satisfies the 9.1 boot AC),</li>
 *   <li>(b) allocation flips a FREE number to ALLOCATED and reduces the FREE count,</li>
 *   <li>(c) concurrent allocations never assign the same MSISDN (FR-13),</li>
 *   <li>(d) termination releases the number to FREE and writes a {@code msisdn.released.v1}
 *       outbox row.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class SubscriptionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    Mediator mediator;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // Reset to a known state: clear written rows and reset the pool to all-FREE.
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("UPDATE msisdn_pool SET status = 'FREE', reserved_until = NULL");
    }

    private long freeCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM msisdn_pool WHERE status = 'FREE'", Long.class);
    }

    // --- (a) boot + migration ---

    @Test
    void context_boots_and_flyway_seeds_the_pool() {
        // 1000 FREE numbers from V2 seed; tables exist (would throw otherwise).
        assertThat(freeCount()).isEqualTo(1000L);
        Long subs = jdbc.queryForObject("SELECT count(*) FROM subscriptions", Long.class);
        assertThat(subs).isZero();
    }

    // --- (b) allocation flips FREE -> ALLOCATED and reduces the FREE count ---

    @Test
    void activation_allocates_an_msisdn_and_reduces_free_count() {
        long before = freeCount();

        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), UUID.randomUUID(), "TARIFF_BASIC", 1));

        assertThat(subscriptionId).isNotNull();
        assertThat(freeCount()).isEqualTo(before - 1);

        String msisdn = jdbc.queryForObject(
                "SELECT msisdn FROM subscriptions WHERE id = ?", String.class, subscriptionId);
        String poolStatus = jdbc.queryForObject(
                "SELECT status FROM msisdn_pool WHERE msisdn = ?", String.class, msisdn);
        assertThat(poolStatus).isEqualTo(MsisdnStatus.ALLOCATED.name());

        // msisdn.allocated.v1 outbox row written atomically with the allocation.
        Long allocated = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'msisdn.allocated.v1' "
                        + "AND aggregate_id = ?", Long.class, subscriptionId.toString());
        assertThat(allocated).isEqualTo(1L);
    }

    // --- (c) concurrent allocations never assign the same MSISDN ---

    @Test
    void concurrent_activations_never_assign_the_same_msisdn() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<UUID>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> mediator.send(
                        new ActivateSubscriptionCommand(UUID.randomUUID(), UUID.randomUUID(), "TARIFF_BASIC", 1)));
            }

            List<Future<UUID>> futures = pool.invokeAll(tasks);
            List<UUID> subscriptionIds = new ArrayList<>();
            for (Future<UUID> f : futures) {
                subscriptionIds.add(f.get());
            }

            // All activations succeeded.
            assertThat(subscriptionIds).hasSize(threads);

            // Every allocated MSISDN is distinct: no double allocation.
            List<String> msisdns = jdbc.queryForList(
                    "SELECT msisdn FROM subscriptions", String.class);
            assertThat(msisdns).hasSize(threads);
            assertThat(msisdns).doesNotHaveDuplicates();

            // Pool accounting matches: exactly `threads` numbers are ALLOCATED.
            Long allocatedInPool = jdbc.queryForObject(
                    "SELECT count(*) FROM msisdn_pool WHERE status = 'ALLOCATED'", Long.class);
            assertThat(allocatedInPool).isEqualTo((long) threads);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- (d) termination releases the number to FREE and emits msisdn.released.v1 ---

    @Test
    void termination_releases_msisdn_to_free_and_writes_released_outbox_row() {
        UUID subscriptionId = mediator.send(
                new ActivateSubscriptionCommand(UUID.randomUUID(), UUID.randomUUID(), "TARIFF_BASIC", 1));
        String msisdn = jdbc.queryForObject(
                "SELECT msisdn FROM subscriptions WHERE id = ?", String.class, subscriptionId);
        long freeAfterAllocate = freeCount();

        mediator.send(new TerminateSubscriptionCommand(subscriptionId));

        // Subscription is TERMINATED.
        String status = jdbc.queryForObject(
                "SELECT status FROM subscriptions WHERE id = ?", String.class, subscriptionId);
        assertThat(status).isEqualTo("TERMINATED");

        // The number is back to FREE; the count recovers by one.
        String poolStatus = jdbc.queryForObject(
                "SELECT status FROM msisdn_pool WHERE msisdn = ?", String.class, msisdn);
        assertThat(poolStatus).isEqualTo(MsisdnStatus.FREE.name());
        assertThat(freeCount()).isEqualTo(freeAfterAllocate + 1);

        // msisdn.released.v1 outbox row written, carrying the released number.
        Long released = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'msisdn.released.v1' "
                        + "AND aggregate_id = ? AND payload->>'msisdn' = ?",
                Long.class, subscriptionId.toString(), msisdn);
        assertThat(released).isEqualTo(1L);
    }
}
