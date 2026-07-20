package com.telco.billing.infrastructure.consumer;

import com.telco.billing.infrastructure.client.ProductCatalogBillingClient;
import com.telco.billing.infrastructure.client.TariffPricingResponse;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.billing.infrastructure.persistence.TariffPriceRepository;
import com.telco.platform.outbox.OutboxService;
import java.math.BigDecimal;
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

/**
 * Verifies the {@code subscription.tariff-changed.v1} billing consumer (Sprint 24 Feature 24.4,
 * design-note D2) against a real Postgres and the REAL inbox: billing record tariff update,
 * orderId-keyed dedup (the record key is the subscriptionId and NOT unique across plan changes),
 * the fail-closed eventType filter, and the transient missing-record path.
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
class TariffChangedBillingConsumerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @MockitoBean private OutboxService outboxService;
    @MockitoBean private ProductCatalogBillingClient catalogClient;

    @Autowired TariffChangedBillingConsumer consumer;
    @Autowired SubscriberBillingRecordRepository subscriberRepo;
    @Autowired TariffPriceRepository tariffPriceRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM subscriber_billing_records");
        jdbc.execute("DELETE FROM tariff_prices");
        org.mockito.Mockito.when(catalogClient.getTariffPricing(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> new TariffPricingResponse(
                        inv.getArgument(0), "Stub Tariff", new BigDecimal("249.90"), "TRY"));
    }

    private UUID seedBillingRecord(String tariffCode) {
        SubscriberBillingRecord record = SubscriberBillingRecord.activated(
                UUID.randomUUID(), UUID.randomUUID(), tariffCode, Instant.now());
        subscriberRepo.save(record);
        return record.getSubscriptionId();
    }

    private ConsumerRecord<String, String> tariffChangedRecord(UUID subscriptionId, String orderId,
                                                               String newTariffCode, long offset,
                                                               String eventType) {
        String json = "{\"subscriptionId\":\"" + subscriptionId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"msisdn\":\"905320000001\","
                + "\"previousTariffCode\":\"POSTPAID-S\","
                + "\"newTariffCode\":\"" + newTariffCode + "\","
                + "\"newTariffVersion\":2,"
                + "\"orderId\":\"" + orderId + "\","
                + "\"changedAt\":" + Instant.now().toEpochMilli() + "}";
        // Record key mirrors production: the outbox aggregate_id, i.e. the SUBSCRIPTION id.
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "subscription.events", 0, offset, subscriptionId.toString(), json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private String tariffOf(UUID subscriptionId) {
        return subscriberRepo.findBySubscriptionId(subscriptionId).orElseThrow().getTariffCode();
    }

    @Test
    void tariff_change_updates_the_billing_record_and_caches_the_new_tariff_price() {
        UUID subscriptionId = seedBillingRecord("POSTPAID-S");

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                "POSTPAID-M", 0L, "subscription.tariff-changed.v1"));

        assertThat(tariffOf(subscriptionId)).isEqualTo("POSTPAID-M");
        // Sprint 24.8 live-E2E regression: without the lazy price cache the next bill run has no
        // TariffPrice for the new code and generates NO invoice for the subscriber.
        assertThat(tariffPriceRepo.findByTariffCode("POSTPAID-M")).isPresent();
    }

    @Test
    void redelivery_of_the_same_order_applies_at_most_once_but_new_orders_apply() {
        UUID subscriptionId = seedBillingRecord("POSTPAID-S");
        String orderId = UUID.randomUUID().toString();

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, orderId,
                "POSTPAID-M", 0L, "subscription.tariff-changed.v1"));
        // Redelivery of the SAME order with different content must be a dedup no-op.
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, orderId,
                "POSTPAID-XL", 1L, "subscription.tariff-changed.v1"));
        assertThat(tariffOf(subscriptionId)).isEqualTo("POSTPAID-M");

        // A SECOND plan change (same record key = subscriptionId, new orderId) must apply.
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                "POSTPAID-L", 2L, "subscription.tariff-changed.v1"));
        assertThat(tariffOf(subscriptionId)).isEqualTo("POSTPAID-L");
    }

    @Test
    void missing_billing_record_throws_transient_and_leaves_no_inbox_row() {
        ConsumerRecord<String, String> record = tariffChangedRecord(UUID.randomUUID(),
                UUID.randomUUID().toString(), "POSTPAID-M", 0L, "subscription.tariff-changed.v1");

        assertThatThrownBy(() -> consumer.onTariffChanged(record))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM inbox_message", Long.class)).isZero();
    }

    @Test
    void wrong_or_missing_event_type_is_ignored() {
        UUID subscriptionId = seedBillingRecord("POSTPAID-S");

        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                "POSTPAID-M", 0L, "subscription.suspended.v1"));
        consumer.onTariffChanged(tariffChangedRecord(subscriptionId, UUID.randomUUID().toString(),
                "POSTPAID-M", 1L, null));

        assertThat(tariffOf(subscriptionId)).isEqualTo("POSTPAID-S");
    }
}
