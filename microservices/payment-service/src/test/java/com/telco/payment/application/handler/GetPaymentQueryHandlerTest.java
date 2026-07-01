package com.telco.payment.application.handler;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.query.GetPaymentQuery;
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
class GetPaymentQueryHandlerTest {

    @Mock private PaymentRepository paymentRepository;

    private GetPaymentQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetPaymentQueryHandler(paymentRepository);
    }

    @Test
    void returns_payment_response_for_known_id() {
        UUID id = UUID.randomUUID();
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("49.99"), "REQ-1");
        when(paymentRepository.findById(id)).thenReturn(Optional.of(payment));

        PaymentResponse response = handler.handle(new GetPaymentQuery(id));

        assertThat(response).isNotNull();
    }

    @Test
    void throws_not_found_for_unknown_id() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetPaymentQuery(id)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
