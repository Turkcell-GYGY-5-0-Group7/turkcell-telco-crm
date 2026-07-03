package com.telco.billing;

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
 * Event-schema contract gate for the invoice domain events (feature 14.1.2, ADR-019, NFR-16).
 *
 * <p>billing-service emits its outbox payloads as package-private records nested in the command
 * handlers rather than in a shared event package. Reflection over record components works regardless
 * of visibility, so each canonical Avro schema snapshot under {@code src/test/resources/avro/} is
 * still compared field-for-field against the emitting nested record (addressed with the {@code $}
 * binary name). A removed, renamed, or unregistered field fails the build, blocking a
 * backward-incompatible change before it reaches Kafka. Pure unit test: no Avro library, Schema
 * Registry, or Spring context.
 *
 * <p>Guarded events: {@code invoice.generated.v1}, {@code invoice.paid.v1}, {@code invoice.overdue.v1}.
 */
class BillingEventSchemaCompatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record SchemaCase(String avscPath, String javaClass) {
        @Override
        public String toString() {
            return avscPath;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("avro/invoice-generated.avsc",
                        "com.telco.billing.application.handler.RunBillCommandHandler$InvoiceGeneratedEvent"),
                new SchemaCase("avro/invoice-paid.avsc",
                        "com.telco.billing.application.handler.MarkInvoicePaidCommandHandler$InvoicePaidEvent"),
                new SchemaCase("avro/invoice-overdue.avsc",
                        "com.telco.billing.application.handler.MarkInvoicesOverdueCommandHandler$InvoiceOverdueEvent"));
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
