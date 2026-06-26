package com.telco.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Schema-compatibility gate (feature 6.4, ADR-019).
 *
 * <p>Verifies that every field declared in the canonical Avro schema (snapshotted in
 * {@code src/test/resources/avro/}) is present in the corresponding Java record under
 * {@code com.telco.customer.application.event}. Fails the build if the hand-written records drift
 * from the published Avro contracts, catching mistakes before they reach Schema Registry.
 *
 * <p>Pure unit test: no Spring context, no database, no containers.
 */
class CustomerEventSchemaCompatTest {

    private static final String EVENT_PACKAGE = "com.telco.customer.application.event";
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "avro/customer-registered.avsc",
            "avro/customer-updated.avsc",
            "avro/customer-kyc-approved.avsc",
            "avro/customer-kyc-rejected.avsc"
    })
    void java_record_contains_all_avro_fields(String schemaResource) throws Exception {
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

        Set<String> javaFields = Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> avroFields = StreamSupport.stream(schema.get("fields").spliterator(), false)
                .map(f -> f.get("name").asText())
                .collect(Collectors.toUnmodifiableSet());

        // Every Avro field must appear in the Java record (forward: schema drives the contract).
        Set<String> missing = avroFields.stream()
                .filter(f -> !javaFields.contains(f))
                .collect(Collectors.toUnmodifiableSet());
        assertThat(missing)
                .as("Avro fields missing from Java record %s (update the record or add a @JsonAlias): %s",
                        avroName, missing)
                .isEmpty();

        // Every Java record field must appear in the Avro schema (reverse: catches unregistered additions).
        Set<String> extra = javaFields.stream()
                .filter(f -> !avroFields.contains(f))
                .collect(Collectors.toUnmodifiableSet());
        assertThat(extra)
                .as("Java record %s has fields not in the Avro schema (add to .avsc or remove from record): %s",
                        avroName, extra)
                .isEmpty();
    }
}
