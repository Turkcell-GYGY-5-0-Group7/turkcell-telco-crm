package com.telco.fraud.application.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Small shared helpers for the four fraud lifecycle consumers reading the {@code subscription.events}
 * topic: the {@code eventType} header (written by the Debezium outbox EventRouter) used to filter the
 * shared topic to a single event type, and the stable inbox dedup key (the envelope {@code eventId},
 * falling back to the record key, then the offset). Mirrors the header/dedup handling in
 * billing-service's {@code Subscription*BillingConsumer}s.
 */
final class ConsumerRecords {

    /** Kafka header the Debezium EventRouter writes the outbox {@code event_type} into. */
    private static final String EVENT_TYPE_HEADER = "eventType";

    private ConsumerRecords() {
    }

    /** UTF-8 value of the {@code eventType} header, or {@code null} when absent. */
    static String eventType(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(EVENT_TYPE_HEADER);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** Stable inbox dedup key: envelope {@code eventId}, else record key, else a per-offset fallback. */
    static String messageId(ConsumerRecord<String, String> record, String eventId) {
        if (eventId != null) {
            return eventId;
        }
        return record.key() != null ? record.key() : "fallback-offset-" + record.offset();
    }
}
