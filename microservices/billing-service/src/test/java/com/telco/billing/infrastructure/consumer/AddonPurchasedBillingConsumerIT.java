package com.telco.billing.infrastructure.consumer;

import com.telco.billing.infrastructure.entity.AddonChargeRecord;
import com.telco.billing.infrastructure.persistence.AddonChargeRecordRepository;
import com.telco.platform.outbox.OutboxService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

/**
 * Verifies the {@code addon.purchased.v1} billing consumer (Sprint 24 Feature 24.3, design-note
 * D3) against a real Postgres and the REAL inbox: one unbilled charge row per purchase with
 * {@code price * quantity}, atomic dedup under Kafka redelivery, and the fail-closed eventType
 * filter. No broker is needed; the consumer is invoked directly with a constructed
 * {@link ConsumerRecord}.
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
class AddonPurchasedBillingConsumerIT {

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

    @Autowired AddonPurchasedBillingConsumer consumer;
    @Autowired AddonChargeRecordRepository chargeRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inbox_message");
        jdbc.execute("DELETE FROM addon_charge_records");
    }

    private ConsumerRecord<String, String> addonPurchasedRecord(String messageId, UUID subscriptionId,
                                                                UUID customerId, int quantity,
                                                                long offset, String eventType) {
        String json = "{\"orderId\":\"" + UUID.randomUUID() + "\","
                + "\"customerId\":\"" + customerId + "\","
                + "\"subscriptionId\":\"" + subscriptionId + "\","
                + "\"addonCode\":\"ADDON-5GB\","
                + "\"addonName\":\"Extra 5GB\","
                + "\"addonType\":\"DATA\","
                + "\"price\":15.00,"
                + "\"currency\":\"TRY\","
                + "\"quantity\":" + quantity + ","
                + "\"allowanceDataMb\":5120,"
                + "\"allowanceMinutes\":null,"
                + "\"allowanceSms\":null,"
                + "\"occurredAt\":\"2026-07-15T10:00:00Z\"}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("addon.events", 0, offset, messageId, json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    void purchase_records_one_unbilled_charge_with_price_times_quantity() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onAddonPurchased(addonPurchasedRecord("item-1", subscriptionId, customerId, 2,
                0L, "addon.purchased.v1"));

        List<AddonChargeRecord> charges =
                chargeRepository.findBySubscriptionIdAndBilledFalse(subscriptionId);
        assertThat(charges).hasSize(1);
        AddonChargeRecord charge = charges.get(0);
        assertThat(charge.getCustomerId()).isEqualTo(customerId);
        assertThat(charge.getAddonCode()).isEqualTo("ADDON-5GB");
        assertThat(charge.getAddonName()).isEqualTo("Extra 5GB");
        assertThat(charge.getPrice()).isEqualByComparingTo("30.00");
        assertThat(charge.getCurrency()).isEqualTo("TRY");
        assertThat(charge.isBilled()).isFalse();
        assertThat(charge.getInvoiceId()).isNull();
    }

    @Test
    void redelivery_of_the_same_message_records_exactly_one_charge() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onAddonPurchased(addonPurchasedRecord("item-dup", subscriptionId, customerId, 1,
                0L, "addon.purchased.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-dup", subscriptionId, customerId, 1,
                1L, "addon.purchased.v1"));

        assertThat(chargeRepository.findBySubscriptionIdAndBilledFalse(subscriptionId)).hasSize(1);
    }

    @Test
    void two_distinct_items_record_two_charges() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onAddonPurchased(addonPurchasedRecord("item-a", subscriptionId, customerId, 1,
                0L, "addon.purchased.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-b", subscriptionId, customerId, 1,
                1L, "addon.purchased.v1"));

        assertThat(chargeRepository.findBySubscriptionIdAndBilledFalse(subscriptionId)).hasSize(2);
    }

    @Test
    void wrong_or_missing_event_type_is_ignored() {
        UUID subscriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onAddonPurchased(addonPurchasedRecord("item-w", subscriptionId, customerId, 1,
                0L, "quota.exceeded.v1"));
        consumer.onAddonPurchased(addonPurchasedRecord("item-n", subscriptionId, customerId, 1,
                1L, null));

        assertThat(chargeRepository.count()).isZero();
    }
}
