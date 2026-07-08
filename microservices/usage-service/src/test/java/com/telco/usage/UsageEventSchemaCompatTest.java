package com.telco.usage;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the usage domain events (ADR-019, NFR-16; extended feature 14.5
 * phase 6).
 *
 * <p>Each Java payload record usage-service publishes or consumes is compared field-for-field <b>and
 * type-for-type, with nullability checked in both directions</b> against the canonical Avro schema
 * loaded directly from {@code platform-event-contracts} (not a hand-copied local {@code .avsc}
 * snapshot). {@code cdr.recorded.v1} is a consumed (not produced) external contract -- checked against
 * {@link com.telco.usage.application.consumer.CdrRecordedEventConsumer.CdrPayload}, the DTO the
 * consumer actually deserializes into.
 *
 * <p>Guarded events: {@code usage.recorded.v1}, {@code quota.threshold-reached.v1},
 * {@code quota.exceeded.v1}, {@code usage.aggregated.v1}, {@code cdr.recorded.v1}.
 */
class UsageEventSchemaCompatTest {

    private static final String AVRO_PACKAGE = "com.telco.platform.events.usage.";
    private static final String EVENT_PACKAGE = "com.telco.usage.application.event.";

    record SchemaCase(String canonicalClassName, String javaClassName) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase(AVRO_PACKAGE + "UsageRecordedV1", EVENT_PACKAGE + "UsageRecordedEvent"),
                new SchemaCase(AVRO_PACKAGE + "QuotaThresholdReachedV1", EVENT_PACKAGE + "QuotaThresholdReachedEvent"),
                new SchemaCase(AVRO_PACKAGE + "QuotaExceededV1", EVENT_PACKAGE + "QuotaExceededEvent"),
                new SchemaCase(AVRO_PACKAGE + "UsageAggregatedV1", EVENT_PACKAGE + "UsageAggregatedEvent"),
                new SchemaCase(AVRO_PACKAGE + "CdrRecordedV1",
                        "com.telco.usage.application.consumer.CdrRecordedEventConsumer$CdrPayload")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase schema) throws Exception {
        Schema avroSchema = AvroContractAssertions.canonicalSchema(schema.canonicalClassName());
        Class<?> javaClass = Class.forName(schema.javaClassName());
        AvroContractAssertions.assertRecordMatchesSchema(avroSchema, javaClass);
    }
}
