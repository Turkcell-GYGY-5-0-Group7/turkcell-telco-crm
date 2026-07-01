package com.telco.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAttemptTest {

    @Test
    void create_sets_all_fields_correctly_for_success_attempt() {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), "REQ-001");

        PaymentAttempt attempt = PaymentAttempt.create(payment, 1, AttemptStatus.SUCCESS, null);

        assertThat(attempt.getId()).isNotNull();
        assertThat(attempt.getPayment()).isSameAs(payment);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUCCESS);
        assertThat(attempt.getErrorMessage()).isNull();
        assertThat(attempt.getAttemptedAt()).isNotNull();
    }

    @Test
    void create_stores_error_message_for_failed_attempt() {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("49.99"), "REQ-002");

        PaymentAttempt attempt = PaymentAttempt.create(payment, 2, AttemptStatus.FAILED, "Card declined");

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.FAILED);
        assertThat(attempt.getErrorMessage()).isEqualTo("Card declined");
        assertThat(attempt.getAttemptNumber()).isEqualTo(2);
    }
}
