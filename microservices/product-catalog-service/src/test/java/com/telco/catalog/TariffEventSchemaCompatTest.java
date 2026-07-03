package com.telco.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Event-schema contract gate for the tariff domain events (feature 14.1.2, ADR-019, NFR-16).
 *
 * <p>Each canonical Avro schema is snapshotted under {@code src/test/resources/avro/} and compared
 * field-for-field against the Java outbox payload record that product-catalog-service actually
 * publishes. The test fails the build when a record drops, renames, or adds a field without the
 * matching contract change, catching a backward-incompatible event change before it reaches Kafka /
 * Schema Registry. No Avro library, Schema Registry, or Spring context is required (the {@code name}
 * field of the snapshot is documentation only; the mapping to the Java class is explicit below).
 *
 * <p>Guarded events: {@code tariff.created.v1}, {@code tariff.price-changed.v1}.
 */
class TariffEventSchemaCompatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record SchemaCase(String avscPath, String javaClass) {
        @Override
        public String toString() {
            return avscPath;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("avro/tariff-created.avsc",
                        "com.telco.catalog.domain.event.TariffCreatedEvent"),
                new SchemaCase("avro/tariff-price-changed.avsc",
                        "com.telco.catalog.domain.event.TariffPriceChangedEvent"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_fields_match_java_record_exactly(SchemaCase schema) throws Exception {
        Set<String> avroFields = avroFieldNames(schema.avscPath());
        Set<String> javaFields = javaRecordComponents(schema.javaClass());

        assertThat(javaFields)
                .as("Avro fields missing from Java record %s (a removed/renamed field is a "
                        + "backward-incompatible change: add a new event version): %s",
                        schema.javaClass(), avroFields)
                .containsAll(avroFields);

        assertThat(avroFields)
                .as("Java record %s declares fields absent from the frozen Avro contract "
                        + "(register the new field in the .avsc snapshot): %s",
                        schema.javaClass(), javaFields)
                .containsAll(javaFields);
    }

    private static Set<String> avroFieldNames(String classpathPath) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathPath)) {
            assertThat(in).as("schema resource not found on classpath: %s", classpathPath).isNotNull();
            JsonNode root = MAPPER.readTree(in);
            return StreamSupport.stream(root.get("fields").spliterator(), false)
                    .map(n -> n.get("name").asText())
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> javaRecordComponents(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        assertThat(clazz.isRecord()).as("%s must be a Java record", className).isTrue();
        return Arrays.stream(clazz.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
