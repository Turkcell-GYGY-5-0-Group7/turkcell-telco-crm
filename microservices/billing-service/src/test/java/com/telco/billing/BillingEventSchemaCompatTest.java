package com.telco.billing;

import com.telco.platform.events.testsupport.AvroContractAssertions;
import org.apache.avro.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Event-schema contract gate for the invoice domain events (feature 14.1.2, ADR-019, NFR-16; extended
 * feature 14.5 phase 6).
 *
 * <p>billing-service emits its outbox payloads as package-private records nested in the command
 * handlers rather than in a shared event package. Reflection over record components works regardless
 * of visibility, so each Java record (addressed with the {@code $} binary name) is compared
 * field-for-field <b>and type-for-type, with nullability checked in both directions</b> against the
 * canonical Avro schema loaded directly from {@code platform-event-contracts} (not a hand-copied local
 * {@code .avsc} snapshot). A removed, renamed, retyped, or nullability-mismatched field fails the
 * build, blocking a backward-incompatible change before it reaches Kafka. Pure unit test: no
 * Avro-serialization, Kafka, Schema Registry, or Spring context.
 *
 * <p>Guarded events: {@code invoice.generated.v1}, {@code invoice.paid.v1}, {@code invoice.overdue.v1}.
 */
class BillingEventSchemaCompatTest {

    record SchemaCase(String canonicalClassName, String javaClass) {
        @Override
        public String toString() {
            return canonicalClassName;
        }
    }

    static Stream<SchemaCase> schemas() {
        return Stream.of(
                new SchemaCase("com.telco.platform.events.invoice.InvoiceGeneratedV1",
                        "com.telco.billing.application.handler.BillRunBatchProcessor$InvoiceGeneratedEvent"),
                new SchemaCase("com.telco.platform.events.invoice.InvoicePaidV1",
                        "com.telco.billing.application.handler.MarkInvoicePaidCommandHandler$InvoicePaidEvent"),
                new SchemaCase("com.telco.platform.events.invoice.InvoiceOverdueV1",
                        "com.telco.billing.application.handler.MarkInvoicesOverdueCommandHandler$InvoiceOverdueEvent"),
                new SchemaCase("com.telco.platform.events.addon.AddonPurchasedV1",
                        "com.telco.billing.infrastructure.consumer.AddonPurchasedBillingConsumer$AddonPurchasedPayload"),
                new SchemaCase("com.telco.platform.events.subscription.SubscriptionTariffChangedV1",
                        "com.telco.billing.infrastructure.consumer.TariffChangedBillingConsumer$TariffChangedPayload"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemas")
    void avro_schema_matches_java_record_exactly(SchemaCase testCase) throws Exception {
        Schema schema = AvroContractAssertions.canonicalSchema(testCase.canonicalClassName());
        Class<?> javaClass = Class.forName(testCase.javaClass());
        AvroContractAssertions.assertRecordMatchesSchema(schema, javaClass);
    }
}
