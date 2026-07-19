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

    // --- Sprint 24 Feature 24.2: item-type factories ---

    @Test
    void create_defaults_to_tariff_item_type_with_no_addon_fields() {
        Order order = Order.create(UUID.randomUUID(), "key-4", new BigDecimal("10.00"), "user-1");

        OrderItem item = OrderItem.create(order, UUID.randomUUID(), "BASIC_V1", 1, "Basic",
                new BigDecimal("10.00"), 1);

        assertThat(item.getItemType()).isEqualTo(OrderItemType.TARIFF);
        assertThat(item.getProductCode()).isNull();
        assertThat(item.getTargetSubscriptionId()).isNull();
        assertThat(item.getAllowanceDataMb()).isNull();
        assertThat(item.getAllowanceMinutes()).isNull();
        assertThat(item.getAllowanceSms()).isNull();
    }

    @Test
    void forTariff_records_target_subscription_for_plan_change_items() {
        Order order = Order.create(UUID.randomUUID(), "key-5", new BigDecimal("20.00"), "user-1");
        UUID target = UUID.randomUUID();

        OrderItem item = OrderItem.forTariff(order, UUID.randomUUID(), "PREMIUM_V2", 2, "Premium",
                new BigDecimal("20.00"), 1, null, null, target);

        assertThat(item.getItemType()).isEqualTo(OrderItemType.TARIFF);
        assertThat(item.getTargetSubscriptionId()).isEqualTo(target);
    }

    @Test
    void forAddon_snapshots_product_and_allowances_and_carries_no_tariff() {
        Order order = Order.create(UUID.randomUUID(), "key-6", new BigDecimal("15.00"), "user-1");
        UUID target = UUID.randomUUID();

        OrderItem item = OrderItem.forAddon(order, "ADDON-5GB", "Extra 5GB",
                new BigDecimal("15.00"), 2, target, 5120L, null, 100L);

        assertThat(item.getItemType()).isEqualTo(OrderItemType.ADDON);
        assertThat(item.getProductCode()).isEqualTo("ADDON-5GB");
        assertThat(item.getTariffName()).isEqualTo("Extra 5GB");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("15.00");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getTargetSubscriptionId()).isEqualTo(target);
        assertThat(item.getAllowanceDataMb()).isEqualTo(5120L);
        assertThat(item.getAllowanceMinutes()).isNull();
        assertThat(item.getAllowanceSms()).isEqualTo(100L);
        assertThat(item.getTariffId()).isNull();
        assertThat(item.getTariffCode()).isNull();
        assertThat(item.getTariffVersion()).isNull();
        assertThat(item.getCampaignId()).isNull();
    }
}
