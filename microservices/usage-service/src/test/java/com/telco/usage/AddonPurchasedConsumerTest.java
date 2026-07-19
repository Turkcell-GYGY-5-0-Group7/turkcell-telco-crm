package com.telco.usage;

import com.telco.usage.application.consumer.AddonPurchasedEventConsumer;
import com.telco.usage.domain.Quota;
import com.telco.usage.infrastructure.persistence.QuotaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code addon.purchased.v1} consumer (Sprint 24 Feature 24.3, design-note D4): quota
 * top-up with per-unit deltas multiplied by quantity, atomic inbox dedup under redelivery, the
 * fail-closed eventType filter, and the TRANSIENT no-quota-row path (throw so redelivery retries
 * after activation provisioning catches up). No broker is needed; the consumer is invoked directly
 * with a constructed {@link ConsumerRecord}.
 */
@SpringBootTest(
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.bootstrap-servers=localhost:9092"
        }
)
@ActiveProfiles("test")
@Testcontainers
class AddonPurchasedConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final Instant PERIOD_START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-08-01T00:00:00Z");
    private static final String OCCURRED_AT = "2026-07-15T10:00:00Z";

    @Autowired AddonPurchasedEventConsumer consumer;
    @Autowired QuotaRepository quotaRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM quotas");
    }

    private UUID provisionQuota(long minutes, long sms, long mb) {
        Quota quota = Quota.create(UUID.randomUUID(), UUID.randomUUID(),
                PERIOD_START, PERIOD_END, minutes, sms, mb);
        quotaRepository.save(quota);
        return quota.getSubscriptionId();
    }

    private ConsumerRecord<String, String> addonPurchasedRecord(String messageId, UUID subscriptionId,
                                                                int quantity, Long dataMb, Long minutes,
                                                                Long sms, long offset, String eventType) {
        String json = "{\"orderId\":\"" + UUID.randomUUID() + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"subscriptionId\":\"" + subscriptionId + "\","
                + "\"addonCode\":\"ADDON-5GB\","
                + "\"addonName\":\"Extra 5GB\","
                + "\"addonType\":\"DATA\","
                + "\"price\":15.00,"
                + "\"currency\":\"TRY\","
                + "\"quantity\":" + quantity + ","
                + "\"allowanceDataMb\":" + dataMb + ","
                + "\"allowanceMinutes\":" + minutes + ","
                + "\"allowanceSms\":" + sms + ","
                + "\"occurredAt\":\"" + OCCURRED_AT + "\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("addon.events", 0, offset, messageId, json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private Quota quotaOf(UUID subscriptionId) {
        return quotaRepository.findFirstBySubscriptionId(subscriptionId).orElseThrow();
    }

    @Test
    void addon_purchase_tops_up_totals_and_remaining() {
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-1", subscriptionId, 1,
                5120L, null, null, 0L, "addon.purchased.v1"));

        Quota quota = quotaOf(subscriptionId);
        assertThat(quota.getMbTotal()).isEqualTo(6144);
        assertThat(quota.getMbRemaining()).isEqualTo(6144);
        assertThat(quota.getMinutesTotal()).isEqualTo(300);
    }

    @Test
    void quantity_multiplies_the_per_unit_deltas() {
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-q2", subscriptionId, 2,
                null, null, 100L, 0L, "addon.purchased.v1"));

        assertThat(quotaOf(subscriptionId).getSmsTotal()).isEqualTo(400);
    }

    @Test
    void redelivery_of_the_same_message_is_idempotent() {
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-dup", subscriptionId, 1,
                5120L, null, null, 0L, "addon.purchased.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-dup", subscriptionId, 1,
                5120L, null, null, 1L, "addon.purchased.v1"));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(6144);
    }

    @Test
    void two_distinct_items_of_the_same_addon_both_apply() {
        // Two order items (distinct aggregate ids -> distinct record keys) must both top up.
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-a", subscriptionId, 1,
                5120L, null, null, 0L, "addon.purchased.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-b", subscriptionId, 1,
                5120L, null, null, 1L, "addon.purchased.v1"));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(11264);
    }

    @Test
    void missing_quota_row_throws_transient_and_leaves_no_inbox_row() {
        UUID subscriptionId = UUID.randomUUID();

        ConsumerRecord<String, String> record = addonPurchasedRecord("item-early", subscriptionId, 1,
                5120L, null, null, 0L, "addon.purchased.v1");
        assertThatThrownBy(() -> consumer.onAddonPurchased(record))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        // The inbox row must roll back with the transaction so redelivery retries the top-up.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_message", Long.class)).isZero();

        // Redelivery after provisioning catches up succeeds.
        Quota quota = Quota.create(subscriptionId, UUID.randomUUID(),
                PERIOD_START, PERIOD_END, 300, 200, 1024);
        quotaRepository.save(quota);
        consumer.onAddonPurchased(addonPurchasedRecord("item-early", subscriptionId, 1,
                5120L, null, null, 1L, "addon.purchased.v1"));
        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(6144);
    }

    @Test
    void wrong_or_missing_event_type_is_ignored() {
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-w", subscriptionId, 1,
                5120L, null, null, 0L, "some.other.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-n", subscriptionId, 1,
                5120L, null, null, 1L, null));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(1024);
    }

    @Test
    void vas_addon_without_allowances_is_a_noop() {
        UUID subscriptionId = provisionQuota(300, 200, 1024);

        consumer.onAddonPurchased(addonPurchasedRecord("item-vas", subscriptionId, 1,
                null, null, null, 0L, "addon.purchased.v1"));

        Quota quota = quotaOf(subscriptionId);
        assertThat(quota.getMbTotal()).isEqualTo(1024);
        assertThat(quota.getMinutesTotal()).isEqualTo(300);
        assertThat(quota.getSmsTotal()).isEqualTo(200);
    }
}
