package com.telco.usage;

import com.telco.usage.application.consumer.TariffChangedEventConsumer;
import com.telco.usage.domain.Quota;
import com.telco.usage.domain.UsageType;
import com.telco.usage.infrastructure.client.ProductCatalogClient;
import com.telco.usage.infrastructure.client.TariffAllowanceResponse;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies the {@code subscription.tariff-changed.v1} consumer (Sprint 24 Feature 24.4,
 * design-note D4): current-period quota reset to the new tariff's allowances preserving used
 * amounts, orderId-keyed dedup (the Kafka record key is the subscriptionId and NOT unique across
 * successive plan changes), the fail-closed eventType filter, and the transient no-quota-row path.
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
class TariffChangedConsumerTest {

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
    private static final long CHANGED_AT = Instant.parse("2026-07-15T10:00:00Z").toEpochMilli();

    @Autowired TariffChangedEventConsumer consumer;
    @Autowired QuotaRepository quotaRepository;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean ProductCatalogClient productCatalogClient;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM quotas");
        when(productCatalogClient.getTariffAllowances(anyString()))
                .thenReturn(new TariffAllowanceResponse(600, 400, 5120));
    }

    private UUID seedQuota(long minutes, long sms, long mb) {
        Quota quota = Quota.create(UUID.randomUUID(), UUID.randomUUID(),
                PERIOD_START, PERIOD_END, minutes, sms, mb);
        quotaRepository.save(quota);
        return quota.getSubscriptionId();
    }

    private ConsumerRecord<String, String> tariffChangedRecord(UUID subscriptionId, String orderId,
                                                               long offset, String eventType) {
        String json = "{\"subscriptionId\":\"" + subscriptionId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"oldTariffCode\":\"TARIFF_BASIC\","
                + "\"newTariffCode\":\"TARIFF_PLUS\","
                + "\"orderId\":\"" + orderId + "\","
                + "\"changedAt\":" + CHANGED_AT + "}";
        // Record key mirrors production: the outbox aggregate_id, i.e. the SUBSCRIPTION id.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "subscription.events", 0, offset, subscriptionId.toString(), json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private Quota quotaOf(UUID subscriptionId) {
        return quotaRepository.findFirstBySubscriptionId(subscriptionId).orElseThrow();
    }

    @Test
    void tariff_change_resets_quota_to_new_allowances_preserving_used() {
        UUID subscriptionId = seedQuota(300, 200, 1024);
        quotaRepository.findFirstBySubscriptionId(subscriptionId).ifPresent(q -> {
            q.decrement(UsageType.DATA, 500);
            quotaRepository.save(q);
        });

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                0L, "subscription.tariff-changed.v1"));

        Quota quota = quotaOf(subscriptionId);
        assertThat(quota.getMbTotal()).isEqualTo(5120);
        assertThat(quota.getMbRemaining()).isEqualTo(4620);
        assertThat(quota.getMinutesTotal()).isEqualTo(600);
        assertThat(quota.getSmsTotal()).isEqualTo(400);
    }

    @Test
    void redelivery_of_the_same_order_reprovisions_at_most_once() {
        UUID subscriptionId = seedQuota(300, 200, 1024);
        String orderId = UUID.randomUUID().toString();

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, orderId, 0L,
                "subscription.tariff-changed.v1"));
        // Simulate drift: if the second delivery re-applied, totals would still be 5120 but a
        // different stub would show. Change the stub to prove the handler did not run again.
        when(productCatalogClient.getTariffAllowances(anyString()))
                .thenReturn(new TariffAllowanceResponse(999, 999, 9999));
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, orderId, 1L,
                "subscription.tariff-changed.v1"));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(5120);
    }

    @Test
    void second_plan_change_with_same_record_key_but_new_order_applies() {
        // The record key (subscriptionId) is identical for both events; dedup must be by orderId.
        UUID subscriptionId = seedQuota(300, 200, 1024);

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                0L, "subscription.tariff-changed.v1"));
        when(productCatalogClient.getTariffAllowances(anyString()))
                .thenReturn(new TariffAllowanceResponse(999, 999, 9999));
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                1L, "subscription.tariff-changed.v1"));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(9999);
    }

    @Test
    void missing_quota_row_throws_transient_and_leaves_no_inbox_row() {
        UUID subscriptionId = UUID.randomUUID();

        ConsumerRecord<String, String> record = tariffChangedRecord(subscriptionId,
                UUID.randomUUID().toString(), 0L, "subscription.tariff-changed.v1");
        assertThatThrownBy(() -> consumer.onTariffChanged(record))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_message", Long.class)).isZero();
    }

    @Test
    void wrong_or_missing_event_type_is_ignored() {
        UUID subscriptionId = seedQuota(300, 200, 1024);

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                0L, "subscription.activated.v1"));
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                1L, null));

        assertThat(quotaOf(subscriptionId).getMbTotal()).isEqualTo(1024);
    }
}
