package com.telco.acceptance.support;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/**
 * Produces {@code cdr.events} messages matching the exact JSON shape usage-service's
 * {@code CdrRecordedEventConsumer.CdrPayload} deserializes (subscriptionId, type, quantity,
 * occurredAt, cdrRef). This is the wire contract the (test-only) CDR simulator in usage-service
 * also produces to - {@code cdr.events} carries plain JSON, not Avro, so no Schema Registry
 * dependency is needed here (AC-03, feature 10.6).
 *
 * <p>usage-service exposes no public HTTP endpoint to record a single CDR (ingestion is
 * event-only), so this is the only way a standalone, gateway-driven suite can drive AC-03.
 */
public final class CdrEventProducer implements AutoCloseable {

    private static final String TOPIC = "cdr.events";

    private final KafkaProducer<String, String> producer;

    public CdrEventProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AcceptanceConfig.KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    /** Sends one CDR for the given subscription; returns the generated {@code cdrRef} (the
     * consumer's idempotency key), useful for correlating in assertions/logs. */
    public String sendCdr(UUID subscriptionId, String usageType, long quantity) {
        String cdrRef = UUID.randomUUID().toString();
        String json = """
                {"subscriptionId":"%s","type":"%s","quantity":%d,"occurredAt":"%s","cdrRef":"%s"}
                """.formatted(subscriptionId, usageType, quantity, Instant.now(), cdrRef).strip();
        producer.send(new ProducerRecord<>(TOPIC, cdrRef, json));
        producer.flush();
        return cdrRef;
    }

    @Override
    public void close() {
        producer.close();
    }
}
