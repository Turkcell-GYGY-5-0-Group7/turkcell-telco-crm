package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.platform.mediator.Mediator;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import java.util.UUID;
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
 * Verifies the all-or-nothing failure path of activation (feature 9.3.1, AC): when the MSISDN pool is
 * exhausted, activation allocates NO number, creates NO subscription, and emits
 * {@code subscription.activation-failed.v1} for saga compensation - all in a single committed
 * transaction.
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
class SubscriptionActivationFailureTest {

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
        jdbc.execute("DELETE FROM outbox_event");
        jdbc.execute("DELETE FROM audit_log");
        jdbc.execute("DELETE FROM subscriptions");
    }

    @Test
    void exhausted_pool_emits_activation_failed_and_allocates_no_number() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Exhaust the pool: no FREE numbers remain.
        jdbc.execute("UPDATE msisdn_pool SET status = 'ALLOCATED'");
        long allocatedBefore = jdbc.queryForObject(
                "SELECT count(*) FROM msisdn_pool WHERE status = 'ALLOCATED'", Long.class);

        UUID result = mediator.send(
                new ActivateSubscriptionCommand(orderId, customerId, "TARIFF_BASIC", 1));

        // No subscription id returned, none persisted.
        assertThat(result).isNull();
        Long subs = jdbc.queryForObject("SELECT count(*) FROM subscriptions", Long.class);
        assertThat(subs).isZero();

        // No allocation happened: the pool is exactly as it was (no extra ALLOCATED, no RESERVED).
        long allocatedAfter = jdbc.queryForObject(
                "SELECT count(*) FROM msisdn_pool WHERE status = 'ALLOCATED'", Long.class);
        assertThat(allocatedAfter).isEqualTo(allocatedBefore);
        Long reserved = jdbc.queryForObject(
                "SELECT count(*) FROM msisdn_pool WHERE status = 'RESERVED'", Long.class);
        assertThat(reserved).isZero();

        // subscription.activation-failed.v1 emitted, keyed by orderId, with the reason code.
        Long failed = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activation-failed.v1' "
                        + "AND aggregate_id = ? AND payload->>'orderId' = ? "
                        + "AND payload->>'reason' = 'MSISDN_POOL_EXHAUSTED'",
                Long.class, orderId.toString(), orderId.toString());
        assertThat(failed).isEqualTo(1L);

        // No success events leaked into the outbox.
        Long activated = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'subscription.activated.v1'",
                Long.class);
        assertThat(activated).isZero();
    }
}
