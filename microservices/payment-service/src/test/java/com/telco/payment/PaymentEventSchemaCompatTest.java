package com.telco.payment;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the payment domain events (feature 14.1.2, ADR-019, NFR-16; extended
 * feature 14.5 phase 6).
 *
 * <p>Each Java outbox payload record payment-service publishes is compared field-for-field <b>and
 * type-for-type, with nullability checked in both directions</b> against the canonical Avro schema
 * loaded directly from {@code platform-event-contracts} (not a hand-copied local {@code .avsc}
 * snapshot). A removed, renamed, retyped, or nullability-mismatched field fails the build, blocking a
 * backward-incompatible change to the saga's payment events before it reaches Kafka. Pure unit test:
 * no Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code payment.completed.v1}, {@code payment.failed.v1},
 * {@code payment.refunded.v1}.
 */
class PaymentEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.payment.PaymentCompletedV1",
                        "com.telco.payment.application.event.PaymentCompletedEvent"),
                new SchemaCase("com.telco.platform.events.payment.PaymentFailedV1",
                        "com.telco.payment.application.event.PaymentFailedEvent"),
                new SchemaCase("com.telco.platform.events.payment.PaymentRefundedV1",
                        "com.telco.payment.application.event.PaymentRefundedEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
