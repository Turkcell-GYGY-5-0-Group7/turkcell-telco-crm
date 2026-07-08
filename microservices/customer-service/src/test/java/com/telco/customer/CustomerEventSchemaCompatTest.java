package com.telco.customer;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Schema-compatibility gate (feature 6.4, ADR-019; extended feature 14.5 phase 6).
 *
 * <p>Verifies that each canonical Avro schema -- loaded directly from the Avro-generated class in
 * {@code platform-event-contracts}, not a hand-copied local {@code .avsc} snapshot -- matches
 * field-for-field <b>and type-for-type, with nullability checked in both directions</b>, the
 * corresponding Java record under {@code com.telco.customer.application.event}. Fails the build if
 * the hand-written records drift from the published Avro contracts, catching mistakes before they
 * reach Schema Registry.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class CustomerEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.customer.application.event";
    private static final String CANONICAL_PACKAGE = "com.telco.platform.events.customer";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "CustomerRegisteredV1",
            "CustomerUpdatedV1",
            "CustomerKycApprovedV1",
            "CustomerKycRejectedV1"
    })
    void java_record_matches_avro_schema(String avroName) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(CANONICAL_PACKAGE + "." + avroName);
        Class<?> recordClass = Class.forName(EVENT_PACKAGE + "." + avroName);
        AvroContractAssertions.assertRecordMatchesSchema(schema, recordClass);
    }
}
