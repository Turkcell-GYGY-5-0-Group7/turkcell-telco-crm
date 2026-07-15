package com.telco.campaign;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the campaign lifecycle events (Feature 21.4.1, ADR-019).
 *
 * <p>Each Java outbox payload record campaign-service publishes is compared field-for-field and
 * type-for-type, with nullability checked in both directions, against the canonical Avro schema
 * loaded directly from {@code platform-event-contracts} (not a hand-copied local {@code .avsc}
 * snapshot). The test fails the build when a record drops, renames, retypes, or adds a field without
 * the matching contract change, catching a backward-incompatible event change before it reaches Kafka
 * / Schema Registry. Pure unit test: no Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code campaign.created.v1}, {@code campaign.activated.v1},
 * {@code campaign.paused.v1}, {@code campaign.expired.v1}, {@code campaign.cancelled.v1}.
 */
class CampaignEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.campaign.CampaignCreatedV1",
                        "com.telco.campaign.domain.event.CampaignCreatedEvent"),
                new SchemaCase("com.telco.platform.events.campaign.CampaignActivatedV1",
                        "com.telco.campaign.domain.event.CampaignActivatedEvent"),
                new SchemaCase("com.telco.platform.events.campaign.CampaignPausedV1",
                        "com.telco.campaign.domain.event.CampaignPausedEvent"),
                new SchemaCase("com.telco.platform.events.campaign.CampaignExpiredV1",
                        "com.telco.campaign.domain.event.CampaignExpiredEvent"),
                new SchemaCase("com.telco.platform.events.campaign.CampaignCancelledV1",
                        "com.telco.campaign.domain.event.CampaignCancelledEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
