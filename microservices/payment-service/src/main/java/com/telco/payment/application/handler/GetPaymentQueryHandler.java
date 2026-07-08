package com.telco.payment.application.handler;

import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.query.GetPaymentQuery;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Retrieves a payment by its internal ID (FR-25). Returns 404 if not found. */
@Component
public class GetPaymentQueryHandler implements QueryHandler<GetPaymentQuery, PaymentResponse> {

    private final PaymentRepository paymentRepository;

    public GetPaymentQueryHandler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse handle(GetPaymentQuery query) {
        return paymentRepository.findById(query.paymentId())
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Payment not found: " + query.paymentId(),
                        Map.of("paymentId", query.paymentId())));
    }
}
