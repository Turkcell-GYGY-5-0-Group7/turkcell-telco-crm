package com.telco.order;

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
 * Event-schema contract gate for the order domain events (feature 14.1.2, ADR-019, NFR-16).
 *
 * <p>Each canonical Avro schema snapshot under {@code src/test/resources/avro/} is compared
 * field-for-field against the Java outbox payload record order-service publishes, including the
 * nested {@code OrderItemPayload} embedded in {@code order.created.v1}. A removed, renamed, or
 * unregistered field fails the build, blocking a backward-incompatible event change before it reaches
 * Kafka. Pure unit test: no Avro library, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code order.created.v1} (and its item element), {@code order.cancelled.v1}.
 */
class OrderEventSchemaCompatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record SchemaCase(String avscPath, String javaClass) {
        @Override
        public String toString() {
            return avscPath;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("avro/order-created.avsc",
                        "com.telco.order.application.event.OrderCreatedEvent"),
                new SchemaCase("avro/order-item.avsc",
                        "com.telco.order.application.event.OrderCreatedEvent$OrderItemPayload"),
                new SchemaCase("avro/order-cancelled.avsc",
                        "com.telco.order.application.event.OrderCancelledEvent"));
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
