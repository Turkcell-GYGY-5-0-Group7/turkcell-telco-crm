package com.telco.order.application.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression gate for the Sprint 24.8 live-E2E connector kill (2026-07-20): the outbox payload
 * column is JSONB and Postgres normalizes numerics (15.00 -> 15), so a whole-number price next to
 * a fractional one produced INT64-vs-FLOAT64 items-array elements, which Debezium's
 * {@code expand.json.payload} cannot union - the order connector task died and the entire order
 * event stream halted. {@code unitPrice} must therefore serialize as a JSON STRING (immune to
 * JSONB normalization; identically typed in every element), and a consumer must still be able to
 * bind it into {@code BigDecimal}.
 */
class OrderCreatedEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unit_price_serializes_as_a_json_string_in_every_item() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent("o-1", "c-1",
                List.of(
                        new OrderCreatedEvent.OrderItemPayload("t-1", "Tariff", new BigDecimal("199.90"),
                                1, null, "TARIFF", null, null),
                        new OrderCreatedEvent.OrderItemPayload("a-1", "Addon", new BigDecimal("15.00"),
                                1, null, "ADDON", "ADDON-5GB", null)),
                new BigDecimal("214.90"), "idem-1", "2026-07-20T00:00:00Z");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        for (JsonNode item : json.get("items")) {
            assertThat(item.get("unitPrice").isTextual())
                    .as("unitPrice must be a JSON string (JSONB-normalization-proof): %s", item)
                    .isTrue();
        }
        assertThat(json.get("items").get(0).get("unitPrice").asText()).isEqualTo("199.90");
        assertThat(json.get("items").get(1).get("unitPrice").asText()).isEqualTo("15.00");

        // A consumer-side record still binds the string into BigDecimal.
        record ItemView(BigDecimal unitPrice) {
        }
        ItemView view = objectMapper.treeToValue(json.get("items").get(1), ItemView.class);
        assertThat(view.unitPrice()).isEqualByComparingTo("15.00");
    }
}
