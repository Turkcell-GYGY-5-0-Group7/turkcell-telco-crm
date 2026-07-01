package com.telco.payment.application.handler;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.query.GetPaymentByOrderQuery;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Retrieves the payment associated with a given order (FR-25). Returns 404 if not found. */
@Component
public class GetPaymentByOrderQueryHandler
        implements QueryHandler<GetPaymentByOrderQuery, PaymentResponse> {

    private final PaymentRepository paymentRepository;

    public GetPaymentByOrderQueryHandler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentResponse handle(GetPaymentByOrderQuery query) {
        return paymentRepository.findByOrderId(query.orderId())
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "No payment found for orderId: " + query.orderId(),
                        Map.of("orderId", query.orderId())));
    }
}
