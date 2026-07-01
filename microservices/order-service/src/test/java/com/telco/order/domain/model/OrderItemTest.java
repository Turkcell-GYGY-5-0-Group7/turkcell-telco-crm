package com.telco.order.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void create_sets_all_fields_and_generates_id() {
        Order order = Order.create(UUID.randomUUID(), "key-1", new BigDecimal("50.00"), "user-1");
        UUID tariffId = UUID.randomUUID();

        OrderItem item = OrderItem.create(order, tariffId, "PREMIUM_V1", 1, "Premium Plan", new BigDecimal("99.00"), 3);

        assertThat(item.getId()).isNotNull();
        assertThat(item.getOrder()).isSameAs(order);
        assertThat(item.getTariffId()).isEqualTo(tariffId);
        assertThat(item.getTariffName()).isEqualTo("Premium Plan");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("99.00");
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    @Test
    void create_generates_unique_ids_across_items_for_same_order() {
        Order order = Order.create(UUID.randomUUID(), "key-2", new BigDecimal("50.00"), "user-1");
        UUID tariffId = UUID.randomUUID();

        OrderItem item1 = OrderItem.create(order, tariffId, "PLAN_A_V1", 1, "Plan A", new BigDecimal("10.00"), 1);
        OrderItem item2 = OrderItem.create(order, tariffId, "PLAN_B_V1", 1, "Plan B", new BigDecimal("20.00"), 1);

        assertThat(item1.getId()).isNotEqualTo(item2.getId());
    }

    @Test
    void create_allows_null_tariff_name_and_unit_price() {
        Order order = Order.create(UUID.randomUUID(), "key-3", new BigDecimal("0.00"), "user-1");
        UUID tariffId = UUID.randomUUID();

        OrderItem item = OrderItem.create(order, tariffId, "UNKNOWN_V0", 0, null, null, 1);

        assertThat(item.getTariffName()).isNull();
        assertThat(item.getUnitPrice()).isNull();
    }
}
