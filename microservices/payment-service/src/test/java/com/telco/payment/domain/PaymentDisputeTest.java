package com.telco.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provisional-hold invariant (ADR-028 Section 5, Sprint 22 Feature 22.5.2's own load-bearing
 * acceptance criteria): {@code markDisputed}/{@code clearDisputed} must change {@code disputed} and
 * NOTHING else - {@code status}/{@code amount}/{@code attempts} bit-for-bit identical before and
 * after, no PSP interaction.
 */
class PaymentDisputeTest {

    private static Payment newPayment() {
        return Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
    }

    @Test
    void new_payment_is_not_disputed() {
        assertThat(newPayment().isDisputed()).isFalse();
    }

    @Test
    void markDisputed_changes_only_the_disputed_flag() {
        Payment payment = newPayment();
        PaymentStatus statusBefore = payment.getStatus();
        BigDecimal amountBefore = payment.getAmount();

        payment.markDisputed();

        assertThat(payment.isDisputed()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(statusBefore);
        assertThat(payment.getAmount()).isEqualByComparingTo(amountBefore);
        assertThat(payment.getAttempts()).isEmpty();
    }

    @Test
    void clearDisputed_changes_only_the_disputed_flag() {
        Payment payment = newPayment();
        payment.markDisputed();
        PaymentStatus statusBefore = payment.getStatus();

        payment.clearDisputed();

        assertThat(payment.isDisputed()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(statusBefore);
    }

    @Test
    void markDisputed_does_not_prevent_a_subsequent_legitimate_refund_of_a_completed_payment() {
        Payment payment = newPayment();
        payment.markCompleted();
        payment.markDisputed();

        // disputed is a scheduler-suppression flag only - markRefunded()'s own COMPLETED guard is
        // the actual gate for whether a refund is legal, independent of the disputed flag.
        payment.markRefunded();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
