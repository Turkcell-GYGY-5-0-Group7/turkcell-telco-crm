package com.telco.platform.events.testsupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

/**
 * Self-test for {@link AvroContractAssertions}, independent of any consuming microservice (feature
 * 14.5 phase 6). Proves the checker itself catches name, type, and nullability drift before it is
 * relied on across 8 services and 32 schemas.
 */
class AvroContractAssertionsSelfTest {

    @Test
    void passes_when_record_matches_schema_exactly() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.usage.UsageRecordedV1");
        assertDoesNotThrow(() -> AvroContractAssertions.assertRecordMatchesSchema(schema, MatchingUsage.class));
    }

    @Test
    void fails_on_type_mismatch() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.usage.UsageRecordedV1");
        AssertionError error = assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertRecordMatchesSchema(schema, WrongTypeUsage.class));
        assertTrue(error.getMessage().contains("recordedAt"),
                "expected the error to identify the mismatched field: " + error.getMessage());
        assertTrue(error.getMessage().contains("STRING") || error.getMessage().contains("expects Java type"),
                "expected the error to describe the type mismatch: " + error.getMessage());
    }

    @Test
    void fails_on_missing_field() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.usage.UsageRecordedV1");
        assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertRecordMatchesSchema(schema, MissingFieldUsage.class));
    }

    @Test
    void fails_on_extra_field() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.usage.UsageRecordedV1");
        assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertRecordMatchesSchema(schema, ExtraFieldUsage.class));
    }

    @Test
    void fails_when_non_nullable_schema_field_is_boxed_nullable_in_java() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.usage.UsageRecordedV1");
        AssertionError error = assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertRecordMatchesSchema(schema, BoxedQuantityUsage.class));
        assertTrue(error.getMessage().contains("may be null"), error.getMessage());
    }

    @Test
    void nested_array_of_records_recurses_into_the_element_type() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.order.OrderCreatedV1");
        assertDoesNotThrow(() -> AvroContractAssertions.assertRecordMatchesSchema(schema, MatchingOrder.class));
    }

    @Test
    void nested_array_of_records_fails_when_element_type_drifts() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.order.OrderCreatedV1");
        assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertRecordMatchesSchema(schema, WrongOrderItem.class));
    }

    @Test
    void runtime_payload_map_passes_when_matching() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.ticket.TicketAssignedV1");
        Map<String, Object> payload = Map.of(
                "ticketId", "t-1",
                "customerId", "c-1",
                "assignedTeam", "tech-support");
        assertDoesNotThrow(() -> AvroContractAssertions.assertPayloadMatchesSchema(schema, payload));
    }

    @Test
    void runtime_payload_map_fails_on_type_mismatch() {
        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.ticket.TicketAssignedV1");
        Map<String, Object> payload = Map.of(
                "ticketId", "t-1",
                "customerId", "c-1",
                "assignedTeam", 42L); // should be a String
        assertThrows(AssertionError.class,
                () -> AvroContractAssertions.assertPayloadMatchesSchema(schema, payload));
    }

    // ---- fixtures ----

    record MatchingUsage(String usageRecordId, String subscriptionId, String type, long quantity,
                          boolean overage, String recordedAt) {
    }

    record WrongTypeUsage(String usageRecordId, String subscriptionId, String type, long quantity,
                           boolean overage, long recordedAt) {
    }

    record MissingFieldUsage(String usageRecordId, String subscriptionId, long quantity,
                              boolean overage, String recordedAt) {
    }

    record ExtraFieldUsage(String usageRecordId, String subscriptionId, String type, long quantity,
                            boolean overage, String recordedAt, String unexpectedExtra) {
    }

    record BoxedQuantityUsage(String usageRecordId, String subscriptionId, String type, Long quantity,
                               boolean overage, String recordedAt) {
    }

    record MatchingOrder(String orderId, String customerId, List<MatchingOrderItem> items,
                          BigDecimal totalAmount, String idempotencyKey, String occurredAt) {
    }

    record MatchingOrderItem(String tariffId, String tariffName, BigDecimal unitPrice, int quantity,
                              String campaignId) {
    }

    record WrongOrderItem(String orderId, String customerId, List<BrokenOrderItem> items,
                           BigDecimal totalAmount, String idempotencyKey, String occurredAt) {
    }

    record BrokenOrderItem(String tariffId, String tariffName, long unitPrice, int quantity) {
    }
}
