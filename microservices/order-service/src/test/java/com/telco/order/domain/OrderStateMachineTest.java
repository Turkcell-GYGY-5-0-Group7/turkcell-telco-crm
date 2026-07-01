package com.telco.order.domain;

import com.telco.order.domain.model.Order;
import com.telco.order.domain.model.OrderStatus;
import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Order} state machine (Sprint 09 Feature 9.4, tech-lead ruling):
 * happy path PENDING -> CONFIRMED -> FULFILLED, the widened cancel (PENDING or CONFIRMED ->
 * CANCELLED), and all illegal transitions (terminal states stay terminal).
 */
class OrderStateMachineTest {

    private static Order newOrder() {
        return Order.create(UUID.randomUUID(), UUID.randomUUID().toString(),
                new BigDecimal("49.99"), "user-1");
    }

    private static Order confirmedOrder() {
        Order o = newOrder();
        o.confirm();
        return o;
    }

    @Test
    void new_order_is_pending() {
        assertThat(newOrder().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // --- confirm() ---

    @Test
    void confirm_pending_transitions_to_confirmed() {
        Order o = newOrder();
        o.confirm();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirm_is_illegal_from_confirmed() {
        Order o = confirmedOrder();
        assertThatThrownBy(o::confirm).isInstanceOf(BusinessRuleException.class);
    }

    // --- fulfill() ---

    @Test
    void fulfill_confirmed_transitions_to_fulfilled() {
        Order o = confirmedOrder();
        o.fulfill();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void fulfill_is_illegal_from_pending() {
        Order o = newOrder();
        assertThatThrownBy(o::fulfill)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only CONFIRMED orders may be fulfilled");
    }

    @Test
    void fulfill_is_illegal_from_fulfilled() {
        Order o = confirmedOrder();
        o.fulfill();
        assertThatThrownBy(o::fulfill).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void fulfill_is_illegal_from_cancelled() {
        Order o = newOrder();
        o.cancel();
        assertThatThrownBy(o::fulfill).isInstanceOf(BusinessRuleException.class);
    }

    // --- fail() ---

    @Test
    void fail_pending_transitions_to_failed() {
        Order o = newOrder();
        o.fail();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void fail_confirmed_transitions_to_failed() {
        Order o = confirmedOrder();
        o.fail();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    void fail_is_illegal_from_fulfilled() {
        Order o = confirmedOrder();
        o.fulfill();
        assertThatThrownBy(o::fail)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING or CONFIRMED orders may be failed");
    }

    @Test
    void fail_is_illegal_from_cancelled() {
        Order o = newOrder();
        o.cancel();
        assertThatThrownBy(o::fail).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void fail_is_illegal_from_failed() {
        Order o = newOrder();
        o.fail();
        assertThatThrownBy(o::fail).isInstanceOf(BusinessRuleException.class);
    }

    // --- widened cancel() ---

    @Test
    void cancel_pending_transitions_to_cancelled() {
        Order o = newOrder();
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_confirmed_transitions_to_cancelled() {
        Order o = confirmedOrder();
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_is_illegal_from_fulfilled() {
        Order o = confirmedOrder();
        o.fulfill();
        assertThatThrownBy(o::cancel)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING or CONFIRMED orders may be cancelled");
    }

    @Test
    void cancel_is_illegal_from_failed() {
        Order o = newOrder();
        o.fail();
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancel_is_illegal_from_cancelled() {
        Order o = newOrder();
        o.cancel();
        assertThatThrownBy(o::cancel).isInstanceOf(BusinessRuleException.class);
    }
}
