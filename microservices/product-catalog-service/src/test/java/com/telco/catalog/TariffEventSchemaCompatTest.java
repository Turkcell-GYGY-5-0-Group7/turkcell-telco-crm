package com.telco.catalog;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the tariff domain events (feature 14.1.2, ADR-019, NFR-16; extended
 * feature 14.5 phase 6).
 *
 * <p>Each Java outbox payload record product-catalog-service publishes is compared field-for-field
 * <b>and type-for-type, with nullability checked in both directions</b> against the canonical Avro
 * schema loaded directly from {@code platform-event-contracts} (not a hand-copied local {@code .avsc}
 * snapshot). The test fails the build when a record drops, renames, retypes, or adds a field without
 * the matching contract change, catching a backward-incompatible event change before it reaches Kafka
 * / Schema Registry. Pure unit test: no Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code tariff.created.v1}, {@code tariff.price-changed.v1}.
 */
class TariffEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.catalog.TariffCreatedV1",
                        "com.telco.catalog.domain.event.TariffCreatedEvent"),
                new SchemaCase("com.telco.platform.events.catalog.TariffPriceChangedV1",
                        "com.telco.catalog.domain.event.TariffPriceChangedEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
