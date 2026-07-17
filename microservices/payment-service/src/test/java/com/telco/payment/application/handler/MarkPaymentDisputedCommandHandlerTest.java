package com.telco.payment.application.handler;

import com.telco.payment.application.command.MarkPaymentDisputedCommand;
import com.telco.payment.domain.Payment;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkPaymentDisputedCommandHandlerTest {

    @Mock private PaymentRepository paymentRepository;

    private MarkPaymentDisputedCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MarkPaymentDisputedCommandHandler(paymentRepository);
    }

    @Test
    void marks_payment_disputed() {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(new MarkPaymentDisputedCommand(payment.getId(), "msg-1"));

        assertThat(payment.isDisputed()).isTrue();
    }

    @Test
    void throws_not_found_when_payment_does_not_exist() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new MarkPaymentDisputedCommand(paymentId, "msg-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
