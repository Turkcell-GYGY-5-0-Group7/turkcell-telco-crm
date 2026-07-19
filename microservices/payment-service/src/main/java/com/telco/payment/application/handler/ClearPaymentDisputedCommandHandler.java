package com.telco.payment.application.handler;

import com.telco.payment.application.command.ClearPaymentDisputedCommand;
import com.telco.payment.domain.Payment;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles {@link ClearPaymentDisputedCommand}. Like marking disputed, clearing it (ADR-028 Section 5,
 * {@code RESOLVED_MERCHANT}) changes {@code disputed} back to {@code false} and NOTHING else.
 */
@Component
public class ClearPaymentDisputedCommandHandler implements CommandHandler<ClearPaymentDisputedCommand, Unit> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClearPaymentDisputedCommandHandler.class);

    private final PaymentRepository paymentRepository;

    public ClearPaymentDisputedCommandHandler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Unit handle(ClearPaymentDisputedCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Payment not found: " + command.paymentId(),
                        Map.of("paymentId", command.paymentId())));

        payment.clearDisputed();
        paymentRepository.save(payment);

        LOGGER.info("Payment disputed flag cleared paymentId={}", command.paymentId());
        return Unit.INSTANCE;
    }
}
