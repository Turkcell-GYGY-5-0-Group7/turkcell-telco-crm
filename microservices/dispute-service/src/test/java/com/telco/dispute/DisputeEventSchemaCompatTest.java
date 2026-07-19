package com.telco.dispute;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the dispute domain events (Sprint 22 Feature 22.6.1, ADR-019).
 *
 * <p>Each Java outbox payload record dispute-service publishes is compared field-for-field and
 * type-for-type, with nullability checked in both directions, against the canonical Avro schema
 * loaded directly from {@code platform-event-contracts} (not a hand-copied local {@code .avsc}
 * snapshot). Pure unit test: no Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code dispute.opened.v1}, {@code dispute.evidence-submitted.v1},
 * {@code dispute.resolved-customer.v1}, {@code dispute.resolved-merchant.v1},
 * {@code dispute.withdrawn.v1}, {@code dispute.closed.v1}.
 */
class DisputeEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.dispute.DisputeOpenedV1",
                        "com.telco.dispute.application.event.DisputeOpenedEvent"),
                new SchemaCase("com.telco.platform.events.dispute.DisputeEvidenceSubmittedV1",
                        "com.telco.dispute.application.event.DisputeEvidenceSubmittedEvent"),
                new SchemaCase("com.telco.platform.events.dispute.DisputeResolvedCustomerV1",
                        "com.telco.dispute.application.event.DisputeResolvedCustomerEvent"),
                new SchemaCase("com.telco.platform.events.dispute.DisputeResolvedMerchantV1",
                        "com.telco.dispute.application.event.DisputeResolvedMerchantEvent"),
                new SchemaCase("com.telco.platform.events.dispute.DisputeWithdrawnV1",
                        "com.telco.dispute.application.event.DisputeWithdrawnEvent"),
                new SchemaCase("com.telco.platform.events.dispute.DisputeClosedV1",
                        "com.telco.dispute.application.event.DisputeClosedEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
