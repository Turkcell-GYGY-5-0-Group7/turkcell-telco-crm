package com.telco.identity;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Event-schema contract gate for the identity domain events (feature 14.5 phase 6, ADR-019).
 *
 * <p>identity-service had no schema-compatibility test infrastructure at all before this feature
 * (see the 14.5 tracking doc, phase 6). This is the first one, built directly against the extended,
 * type-and-nullability-aware checker rather than the older field-name-only pattern other services
 * started with, and against the canonical Avro schema loaded from {@code platform-event-contracts}
 * directly -- never a local {@code .avsc} snapshot.
 *
 * <p>Verifies that each canonical Avro schema matches field-for-field <b>and type-for-type, with
 * nullability checked in both directions</b>, the corresponding Java record under
 * {@code com.telco.identity.application.event}. A removed, renamed, retyped, or
 * nullability-mismatched field fails the build, blocking a backward-incompatible event change before
 * it reaches Kafka.
 *
 * <p>Guarded events: {@code user.created.v1}, {@code user.deleted.v1}.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class IdentityEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.identity.application.event";
    private static final String CANONICAL_PACKAGE = "com.telco.platform.events.identity";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "UserCreatedV1",
            "UserDeletedV1"
    })
    void java_record_matches_avro_schema(String avroName) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(CANONICAL_PACKAGE + "." + avroName);
        Class<?> recordClass = Class.forName(EVENT_PACKAGE + "." + avroName);
        AvroContractAssertions.assertRecordMatchesSchema(schema, recordClass);
    }
}
