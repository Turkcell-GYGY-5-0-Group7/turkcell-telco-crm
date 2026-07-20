package com.telco.fraud;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Schema-compatibility gate for the three fraud-service outbound events (Feature 23.4.1, ADR-019,
 * ADR-029 Section 5).
 *
 * <p>Verifies that each canonical Avro schema -- loaded directly from the Avro-generated class in
 * {@code platform-event-contracts}, not a hand-copied local {@code .avsc} snapshot -- matches
 * field-for-field <b>and type-for-type, with nullability checked in both directions</b>, the
 * corresponding Java outbox payload record under {@code com.telco.fraud.application.event}. Fails the
 * build if a hand-written record drifts from the published contract, catching mistakes before they
 * reach Schema Registry. Mirrors subscription-service's
 * {@code MsisdnEventSchemaCompatTest}/{@code SubscriptionEventSchemaCompatTest} and campaign-service's
 * {@code CampaignEventSchemaCompatTest}.
 *
 * <p>Guarded events: {@code fraud.signal-raised.v1}, {@code fraud.case-opened.v1},
 * {@code fraud.case-resolved.v1}.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class FraudEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.fraud.application.event";
    private static final String CANONICAL_PACKAGE = "com.telco.platform.events.fraud";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "FraudSignalRaisedV1",
            "FraudCaseOpenedV1",
            "FraudCaseResolvedV1"
    })
    void java_record_matches_avro_schema(String avroName) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(CANONICAL_PACKAGE + "." + avroName);
        Class<?> recordClass = Class.forName(EVENT_PACKAGE + "." + avroName);
        AvroContractAssertions.assertRecordMatchesSchema(schema, recordClass);
    }
}
