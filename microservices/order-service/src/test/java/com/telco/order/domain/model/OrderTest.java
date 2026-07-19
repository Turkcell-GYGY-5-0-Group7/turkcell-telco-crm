package com.telco.order.domain.model;

import com.telco.order.domain.model.OrderStatus;
import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "key-001";
    private static final BigDecimal TOTAL = new BigDecimal("99.99");
    private static final String USER_ID = "user-sub-001";

    @Test
    void create_initialises_pending_status_with_all_fields() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(order.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(TOTAL);
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void addItem_appends_item_and_returns_it_with_correct_fields() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        UUID tariffId = UUID.randomUUID();

        OrderItem item = order.addItem(tariffId, "BASIC_V1", 1, "Basic Plan", new BigDecimal("49.99"), 2);

        assertThat(order.getItems()).hasSize(1);
        assertThat(item.getTariffId()).isEqualTo(tariffId);
        assertThat(item.getTariffName()).isEqualTo("Basic Plan");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("49.99");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void addItem_with_campaign_records_campaign_id_and_code() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        UUID tariffId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        OrderItem item = order.addItem(tariffId, "BASIC_V1", 1, "Basic Plan",
                new BigDecimal("37.49"), 1, campaignId, "SUMMER25");

        assertThat(item.getCampaignId()).isEqualTo(campaignId);
        assertThat(item.getCampaignCode()).isEqualTo("SUMMER25");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("37.49");
    }

    @Test
    void cancel_transitions_pending_order_to_cancelled() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void cancel_throws_when_order_already_cancelled() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void cancel_succeeds_when_order_is_confirmed() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        order.confirm();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_throws_when_order_is_fulfilled() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        order.confirm();
        order.fulfill();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("FULFILLED");
    }

    @Test
    void confirm_transitions_pending_order_to_confirmed() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void confirm_throws_when_order_is_not_pending() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);
        order.cancel();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void fail_sets_status_to_failed() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        order.fail();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void getItems_returns_unmodifiable_view() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        assertThatThrownBy(() -> order.getItems().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Sprint 24 Feature 24.2: order kinds ---

    @Test
    void create_without_order_type_defaults_to_new_line() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID);

        assertThat(order.getOrderType()).isEqualTo(OrderType.NEW_LINE);
    }

    @Test
    void create_persists_the_derived_order_type() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID, OrderType.PLAN_CHANGE);

        assertThat(order.getOrderType()).isEqualTo(OrderType.PLAN_CHANGE);
    }

    @Test
    void addAddonItem_appends_an_addon_line_with_snapshot_fields() {
        Order order = Order.create(CUSTOMER_ID, IDEMPOTENCY_KEY, TOTAL, USER_ID, OrderType.ADDON);
        UUID target = UUID.randomUUID();

        OrderItem item = order.addAddonItem("ADDON-5GB", "Extra 5GB", new BigDecimal("15.00"), 1,
                target, 5120L, null, null);

        assertThat(order.getItems()).hasSize(1);
        assertThat(item.getItemType()).isEqualTo(OrderItemType.ADDON);
        assertThat(item.getProductCode()).isEqualTo("ADDON-5GB");
        assertThat(item.getTargetSubscriptionId()).isEqualTo(target);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void create_generates_distinct_id_per_instance() {
        Order first = Order.create(CUSTOMER_ID, "key-a", TOTAL, USER_ID);
        Order second = Order.create(CUSTOMER_ID, "key-b", TOTAL, USER_ID);

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }
}
