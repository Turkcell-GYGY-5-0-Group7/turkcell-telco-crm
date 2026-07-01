package com.telco.payment.application.handler;

import com.telco.payment.application.command.RefundPaymentCommand;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.event.PaymentRefundedEvent;
import com.telco.payment.domain.Payment;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Handles {@link RefundPaymentCommand}: enforces COMPLETED -> REFUNDED transition, calls the
 * PSP mock refund, and emits {@code payment.refunded.v1} (FR-27).
 *
 * <p>The domain entity enforces the state-machine invariant; any illegal transition throws
 * {@link com.telco.platform.common.exception.BusinessRuleException} (HTTP 422).
 */
@Component
public class RefundPaymentCommandHandler
        implements CommandHandler<RefundPaymentCommand, PaymentResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundPaymentCommandHandler.class);
    private static final String OUTBOX_AGGREGATE_TYPE = "payment";
    private static final String EVENT_REFUNDED = "payment.refunded.v1";

    private final PaymentRepository paymentRepository;
    private final PspAdapter pspAdapter;
    private final OutboxService outboxService;

    public RefundPaymentCommandHandler(PaymentRepository paymentRepository,
                                       PspAdapter pspAdapter,
                                       OutboxService outboxService) {
        this.paymentRepository = paymentRepository;
        this.pspAdapter = pspAdapter;
        this.outboxService = outboxService;
    }

    @Override
    public PaymentResponse handle(RefundPaymentCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Payment not found: " + command.paymentId(),
                        Map.of("paymentId", command.paymentId())));

        // Domain rule: only COMPLETED -> REFUNDED; BusinessRuleException thrown by entity if violated.
        payment.markRefunded();

        // Call mock PSP refund (always succeeds in MVP; exceptions propagate as DependencyFailureException).
        pspAdapter.refund(
                payment.getPaymentRequestId(),
                payment.getAmount(),
                payment.getCustomerId().toString());

        paymentRepository.save(payment);

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE, payment.getId().toString(), EVENT_REFUNDED,
                new PaymentRefundedEvent(
                        payment.getId().toString(),
                        payment.getOrderId().toString(),
                        payment.getCustomerId().toString(),
                        payment.getAmount(),
                        command.reason(),
                        Instant.now().toString()));

        LOGGER.info("Payment refunded paymentId={} reason={}", payment.getId(), command.reason());
        return PaymentResponse.from(payment);
    }
}
