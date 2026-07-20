package com.telco.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telco.platform.common.exception.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Subscription} lifecycle state machine (9.2.1, FR-14, FR-15).
 * Framework-independent: no Spring, no database.
 */
class SubscriptionStateMachineTest {

    private static Subscription newActive() {
        return Subscription.activate(UUID.randomUUID(), "905320000000", "TARIFF_BASIC", 1);
    }

    // --- Initial state ---

    @Test
    void activate_starts_in_active_state() {
        Subscription s = newActive();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getActivatedAt()).isNotNull();
        assertThat(s.getTerminatedAt()).isNull();
    }

    // --- Legal transitions ---

    @Test
    void active_can_be_suspended() {
        Subscription s = newActive();
        s.suspend();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.SUSPENDED);
    }

    @Test
    void suspended_can_be_reactivated() {
        Subscription s = newActive();
        s.suspend();
        s.reactivate();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void active_can_be_terminated() {
        Subscription s = newActive();
        s.terminate();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);
        assertThat(s.getTerminatedAt()).isNotNull();
    }

    @Test
    void suspended_can_be_terminated() {
        Subscription s = newActive();
        s.suspend();
        s.terminate();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.TERMINATED);
        assertThat(s.getTerminatedAt()).isNotNull();
    }

    @Test
    void suspend_reactivate_can_repeat() {
        Subscription s = newActive();
        s.suspend();
        s.reactivate();
        s.suspend();
        s.reactivate();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    // --- Illegal transitions throw BusinessRuleException ---

    @Test
    void suspend_when_already_suspended_throws() {
        Subscription s = newActive();
        s.suspend();
        assertThatThrownBy(s::suspend).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void suspend_when_terminated_throws() {
        Subscription s = newActive();
        s.terminate();
        assertThatThrownBy(s::suspend).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reactivate_when_active_throws() {
        Subscription s = newActive();
        assertThatThrownBy(s::reactivate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reactivate_when_terminated_throws() {
        Subscription s = newActive();
        s.terminate();
        assertThatThrownBy(s::reactivate).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void terminate_when_already_terminated_throws() {
        Subscription s = newActive();
        s.terminate();
        assertThatThrownBy(s::terminate).isInstanceOf(BusinessRuleException.class);
    }

    // --- changeTariff (FR-09 package change, Sprint 24 Feature 24.4, design-note D2) ---

    @Test
    void change_tariff_when_active_updates_code_and_version() {
        Subscription s = newActive();

        s.changeTariff("TARIFF_PLUS", 3);

        assertThat(s.getTariffCode()).isEqualTo("TARIFF_PLUS");
        assertThat(s.getTariffVersion()).isEqualTo(3);
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void change_tariff_when_suspended_throws() {
        Subscription s = newActive();
        s.suspend();
        assertThatThrownBy(() -> s.changeTariff("TARIFF_PLUS", 3))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void change_tariff_when_terminated_throws() {
        Subscription s = newActive();
        s.terminate();
        assertThatThrownBy(() -> s.changeTariff("TARIFF_PLUS", 3))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void change_tariff_to_the_same_code_throws() {
        Subscription s = newActive();
        assertThatThrownBy(() -> s.changeTariff(s.getTariffCode(), 9))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- FR-15: a customer may hold multiple ACTIVE subscriptions ---

    @Test
    void one_customer_can_hold_multiple_active_subscriptions() {
        UUID customerId = UUID.randomUUID();
        Subscription a = Subscription.activate(customerId, "905320000001", "TARIFF_BASIC", 1);
        Subscription b = Subscription.activate(customerId, "905320000002", "TARIFF_PLUS", 2);

        assertThat(a.getCustomerId()).isEqualTo(customerId);
        assertThat(b.getCustomerId()).isEqualTo(customerId);
        assertThat(a.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(b.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(a.getMsisdn()).isNotEqualTo(b.getMsisdn());
    }
}
