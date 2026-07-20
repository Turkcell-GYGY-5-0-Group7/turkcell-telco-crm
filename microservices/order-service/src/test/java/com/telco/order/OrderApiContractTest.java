package com.telco.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.telco.order.application.dto.OrderItemResponse;
import com.telco.order.application.dto.OrderResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Provider-side (consumer-driven) API contract gate for the order read model returned by
 * {@code GET /api/v1/orders/{id}} and {@code GET /internal/orders/{id}} (feature 14.1.2, NFR-16).
 * order-service is the provider; subscription-service is the consumer on the saga activation path.
 *
 * <p>subscription-service binds only a subset of the response:
 * <ul>
 *   <li>{@code OrderClientResponse}: customerId, status, items</li>
 *   <li>{@code OrderItemClientResponse}: tariffCode, tariffVersion</li>
 * </ul>
 *
 * <p>Dropping or renaming any of these breaks the {@code payment.completed.v1} activation hop and
 * fails this test. Adding fields is allowed (the consumer sets {@code JsonIgnoreProperties}), so this
 * is a subset assertion.
 */
class OrderApiContractTest {

    private static final Set<String> ORDER_REQUIRED_FIELDS = Set.of("customerId", "status", "items");
    private static final Set<String> ITEM_REQUIRED_FIELDS = Set.of("tariffCode", "tariffVersion");

    @Test
    void order_response_exposes_fields_the_subscription_activation_path_consumes() {
        assertThat(recordFields(OrderResponse.class))
                .as("OrderResponse must keep the fields subscription-service binds during activation "
                        + "(customerId is authoritative, items carries the tariff snapshot); a "
                        + "removed/renamed field is a breaking API change (NFR-16)")
                .containsAll(ORDER_REQUIRED_FIELDS);
    }

    @Test
    void order_item_response_exposes_tariff_snapshot_fields() {
        assertThat(recordFields(OrderItemResponse.class))
                .as("OrderItemResponse must keep the tariff snapshot fields subscription-service reads "
                        + "for activation; a removed/renamed field is a breaking API change (NFR-16)")
                .containsAll(ITEM_REQUIRED_FIELDS);
    }

    private static Set<String> recordFields(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
