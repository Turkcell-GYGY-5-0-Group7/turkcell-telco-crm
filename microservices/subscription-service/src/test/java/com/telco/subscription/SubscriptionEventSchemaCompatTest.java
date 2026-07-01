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
 * Schema-compatibility gate for the subscription lifecycle events (feature 9.3, ADR-019).
 *
 * <p>Verifies that each canonical Avro schema (snapshotted in {@code src/test/resources/avro/}) maps
 * field-for-field AND in declaration order to the corresponding Java record under
 * {@code com.telco.subscription.application.event}. Fails the build if the hand-written records drift
 * from the published contracts, catching mistakes before they reach Schema Registry. Mirrors
 * {@link MsisdnEventSchemaCompatTest}; the order assertion matters because the outbox JSON payload and
 * the Avro serializer must agree on field order.
 *
 * <p>UUID fields serialize as Avro {@code string} (Java {@code String}); timestamps serialize as Avro
 * {@code long}/{@code timestamp-millis} (Java {@code long}); the nullable {@code subscriptionId} on
 * the activation-failed event is an Avro union {@code ["null","string"]} carried as a {@code String}.
 * This test only checks field presence and order, which is what the serializer binds by.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class SubscriptionEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.subscription.application.event";
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "avro/subscription-activated.avsc",
            "avro/subscription-suspended.avsc",
            "avro/subscription-terminated.avsc",
            "avro/subscription-activation-failed.avsc"
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
