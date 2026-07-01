package com.telco.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class UsageEventSchemaCompatTest {

    private static final String AVRO_PACKAGE = "com.telco.usage.application.event.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record SchemaCase(String avscPath, String javaClassName) {}

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("avro/usage-recorded.avsc", AVRO_PACKAGE + "UsageRecordedEvent"),
                new SchemaCase("avro/quota-threshold-reached.avsc", AVRO_PACKAGE + "QuotaThresholdReachedEvent"),
                new SchemaCase("avro/quota-exceeded.avsc", AVRO_PACKAGE + "QuotaExceededEvent")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_fields_match_java_record_components(SchemaCase schema) throws Exception {
        Set<String> avroFields = avroFieldNames(schema.avscPath());
        Set<String> javaFields = javaRecordComponents(schema.javaClassName());

        assertThat(avroFields)
                .as("Avro fields in %s not covered by Java record %s",
                        schema.avscPath(), schema.javaClassName())
                .isSubsetOf(javaFields);

        assertThat(javaFields)
                .as("Java record components in %s not present in Avro schema %s",
                        schema.javaClassName(), schema.avscPath())
                .isSubsetOf(avroFields);
    }

    private static Set<String> avroFieldNames(String classpathPath) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathPath)) {
            assertThat(in).as("resource not found: " + classpathPath).isNotNull();
            JsonNode root = MAPPER.readTree(in);
            return StreamSupport.stream(root.get("fields").spliterator(), false)
                    .map(n -> n.get("name").asText())
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> javaRecordComponents(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        return Arrays.stream(clazz.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
