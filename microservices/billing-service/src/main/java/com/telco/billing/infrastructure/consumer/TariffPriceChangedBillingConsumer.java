package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.RecordTariffPriceChangedCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Consumes {@code tariff.price-changed.v1} from {@code tariff.events} and refreshes billing's
 * local tariff price mirror (FR-08). Closes the stale-price gap: the mirror was previously seeded
 * once per tariff code and never invalidated, so a catalog reprice silently never reached invoices.
 *
 * <p>Follows this service's manual-inbox consumer convention (type filter FIRST, fail closed;
 * {@code InboxService.firstSeen} dedup; dedicated consumer group - see
 * {@link SubscriptionSuspendedBillingConsumer}'s javadoc for why the group id is never shared).
 * Dedup keys on the Kafka record key (the outbox event id set by the Debezium EventRouter);
 * the payload itself carries no eventId envelope field.
 */
@Component
public class TariffPriceChangedBillingConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TariffPriceChangedBillingConsumer.class);
    private static final String CONSUMER_NAME = "TariffPriceChangedBillingConsumer";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String EVENT_TYPE = "tariff.price-changed.v1";

    private final Mediator mediator;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    public TariffPriceChangedBillingConsumer(Mediator mediator, InboxService inboxService,
                                             ObjectMapper objectMapper) {
        this.mediator = mediator;
        this.inboxService = inboxService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "tariff.events", groupId = "billing-service-tariff-price-changed",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onTariffPriceChanged(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, EVENT_TYPE_HEADER);
        if (!EVENT_TYPE.equals(eventType)) {
            LOGGER.debug("Ignoring tariff event of type={} key={}", eventType, record.key());
            return;
        }

        try {
            Payload payload = objectMapper.readValue(record.value(), Payload.class);
            if (payload.code() == null || payload.newMonthlyFee() == null) {
                LOGGER.warn("Ignoring tariff.price-changed.v1 without code/fee key={}", record.key());
                return;
            }

            String messageId = record.key() != null ? record.key()
                    : "fallback-offset-" + record.offset();
            if (!inboxService.firstSeen(messageId, CONSUMER_NAME)) {
                LOGGER.info("Duplicate tariff.price-changed.v1 messageId={}", messageId);
                return;
            }

            Instant changedAt = payload.changedAt() != null
                    ? Instant.parse(payload.changedAt()) : Instant.now();

            mediator.send(new RecordTariffPriceChangedCommand(
                    payload.code(), payload.newMonthlyFee(), payload.currency(), changedAt));
        } catch (Exception e) {
            LOGGER.error("Failed to process tariff.price-changed.v1 key={} offset={}",
                    record.key(), record.offset(), e);
            throw new RuntimeException("tariff.price-changed.v1 billing consumer failed", e);
        }
    }

    /** Returns the UTF-8 value of the last header with the given key, or {@code null} if absent. */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * JSON shape of {@code tariff.price-changed.v1} (mirrors catalog's TariffPriceChangedEvent;
     * {@code changedAt} is an ISO-8601 string per the Avro contract, unlike the epoch-millis
     * subscription events). Unknown fields ignored for forward-compatibility (ADR-019).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String code, BigDecimal newMonthlyFee, String currency, String changedAt) {
    }
}
