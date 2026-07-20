package com.telco.payment.api;

import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.application.dto.ChargePaymentRequest;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.dto.RefundPaymentRequest;
import com.telco.payment.application.query.GetPaymentByOrderQuery;
import com.telco.payment.application.query.GetPaymentQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Payment API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public PaymentController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /**
     * Manual charge override. Normally charges are triggered automatically by the inbox consumer
     * on {@code order.created.v1}. This endpoint is provided for ADMIN use only.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PaymentResponse> charge(@Valid @RequestBody ChargePaymentRequest request) {
        // Admin/non-saga path: no Kafka message, so supply a FRESH unique inbox key per request. The
        // atomic inbox guard is then a harmless per-request no-op and never short-circuits a repeat
        // call - the handler's paymentRequestId lookup remains the source of charge idempotency, so a
        // second POST with the same paymentRequestId still returns the existing payment.
        String messageId = "admin-" + UUID.randomUUID();
        ChargePaymentCommand command = new ChargePaymentCommand(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.invoiceId(),
                request.paymentRequestId(),
                messageId,
                request.method());
        return responses.ok(mediator.send(command));
    }

    /** Returns a payment by its internal ID. ADMIN or SUBSCRIBER role required. */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBSCRIBER')")
    public ApiResult<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return responses.ok(mediator.query(new GetPaymentQuery(paymentId)));
    }

    /** Returns the payment for the given order. ADMIN or SUBSCRIBER role required. */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBSCRIBER')")
    public ApiResult<PaymentResponse> getPaymentByOrder(@PathVariable UUID orderId) {
        return responses.ok(mediator.query(new GetPaymentByOrderQuery(orderId)));
    }

    /** Refunds a completed payment. ADMIN only. */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<PaymentResponse> refund(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundPaymentRequest request) {
        // Admin/non-saga path: no Kafka message, so supply a fresh unique inbox key. The atomic inbox
        // guard is then a per-request no-op; the COMPLETED->REFUNDED domain rule still prevents a
        // double refund.
        String messageId = "admin-" + UUID.randomUUID();
        return responses.ok(
                mediator.send(new RefundPaymentCommand(paymentId, request.reason(), messageId)));
    }
}
