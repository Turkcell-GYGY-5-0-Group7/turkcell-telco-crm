package com.telco.order;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the order domain events (feature 14.1.2, ADR-019, NFR-16; extended
 * feature 14.5 phase 6).
 *
 * <p>Each Java outbox payload record order-service publishes -- including the nested
 * {@code OrderItemPayload} embedded in {@code order.created.v1} -- is compared field-for-field
 * <b>and type-for-type, with nullability checked in both directions</b> against the canonical Avro
 * schema loaded directly from {@code platform-event-contracts} (not a hand-copied local
 * {@code .avsc} snapshot: see the 14.5 tracking doc, phase 6, for why that check was self-referential
 * and never actually verified the governed contract). A removed, renamed, retyped, or
 * nullability-mismatched field fails the build, blocking a backward-incompatible event change before
 * it reaches Kafka. Pure unit test: no Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code order.created.v1} (and its nested item), {@code order.cancelled.v1}.
 */
class OrderEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.order.OrderCreatedV1",
                        "com.telco.order.application.event.OrderCreatedEvent"),
                new SchemaCase("com.telco.platform.events.order.OrderCancelledV1",
                        "com.telco.order.application.event.OrderCancelledEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
