package com.telco.payment.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static Payment pending() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), "REQ-" + UUID.randomUUID());
    }

    @Test
    void create_initialises_pending_status_with_positive_amount() {
        Payment payment = pending();

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("49.99");
        assertThat(payment.getAttempts()).isEmpty();
    }

    @Test
    void create_rejects_zero_amount() {
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, "REQ-0"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_rejects_negative_amount() {
        assertThatThrownBy(() -> Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("-1.00"), "REQ-NEG"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markCompleted_transitions_to_completed() {
        Payment payment = pending();

        payment.markCompleted();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void markCompleted_throws_when_already_refunded() {
        Payment payment = pending();
        payment.markCompleted();
        payment.markRefunded();

        assertThatThrownBy(payment::markCompleted)
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markFailed_transitions_to_failed() {
        Payment payment = pending();

        payment.markFailed();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void markFailed_throws_when_payment_is_completed() {
        Payment payment = pending();
        payment.markCompleted();

        assertThatThrownBy(payment::markFailed)
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void markRefunded_transitions_completed_to_refunded() {
        Payment payment = pending();
        payment.markCompleted();

        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void markRefunded_throws_when_payment_is_not_completed() {
        Payment payment = pending();

        assertThatThrownBy(payment::markRefunded)
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void addAttempt_appended_to_unmodifiable_attempts_list() {
        Payment payment = pending();
        PaymentAttempt attempt = PaymentAttempt.create(payment, 1, AttemptStatus.SUCCESS, null);

        payment.addAttempt(attempt);

        assertThat(payment.getAttempts()).hasSize(1);
        assertThatThrownBy(() -> payment.getAttempts().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
