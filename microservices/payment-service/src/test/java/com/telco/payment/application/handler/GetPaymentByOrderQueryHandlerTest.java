package com.telco.payment.application.handler;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.query.GetPaymentByOrderQuery;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPaymentByOrderQueryHandlerTest {

    @Mock private PaymentRepository paymentRepository;

    private GetPaymentByOrderQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetPaymentByOrderQueryHandler(paymentRepository);
    }

    @Test
    void returns_payment_for_known_order() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.create(orderId, UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        PaymentResponse response = handler.handle(new GetPaymentByOrderQuery(orderId));

        assertThat(response).isNotNull();
    }

    @Test
    void throws_not_found_when_no_payment_for_order() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetPaymentByOrderQuery(orderId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
