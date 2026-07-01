package com.telco.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Schema-compatibility gate for the MSISDN events (feature 9.2.2, ADR-019).
 *
 * <p>Verifies that each canonical Avro schema (snapshotted in {@code src/test/resources/avro/}) maps
 * field-for-field AND in declaration order to the corresponding Java record under
 * {@code com.telco.subscription.application.event}. Fails the build if the hand-written records drift
 * from the published contracts, catching mistakes before they reach Schema Registry. Mirrors
 * customer-service's CustomerEventSchemaCompatTest, with an added order assertion because the outbox
 * payload and the Avro serializer must agree on field order.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class MsisdnEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.subscription.application.event";
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "avro/msisdn-allocated.avsc",
            "avro/msisdn-released.avsc"
    })
    void java_record_matches_avro_fields_in_order(String schemaResource) throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(schemaResource);
        assertThat(stream)
                .as("schema resource not found on classpath: %s", schemaResource)
                .isNotNull();

        JsonNode schema = mapper.readTree(stream);
        String avroName = schema.get("name").asText();

        Class<?> recordClass = Class.forName(EVENT_PACKAGE + "." + avroName);
        assertThat(recordClass.isRecord())
                .as("%s must be a Java record", avroName)
                .isTrue();

        List<String> javaFields = Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        List<String> avroFields = StreamSupport.stream(schema.get("fields").spliterator(), false)
                .map(f -> f.get("name").asText())
                .collect(Collectors.toList());

        // Field-for-field AND in order: the record component list must equal the Avro field list.
        assertThat(javaFields)
                .as("Java record %s must declare the same fields in the same order as the Avro schema",
                        avroName)
                .containsExactlyElementsOf(avroFields);
    }
}
