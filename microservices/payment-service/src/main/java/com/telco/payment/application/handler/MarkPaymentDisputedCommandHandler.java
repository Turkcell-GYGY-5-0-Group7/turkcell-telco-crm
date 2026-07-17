package com.telco.payment.application.handler;

import com.telco.payment.application.command.MarkPaymentDisputedCommand;
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
 * Handles {@link MarkPaymentDisputedCommand}. Provisional-hold invariant (ADR-028 Section 5,
 * load-bearing): changes {@code Payment.disputed} from {@code false} to {@code true} and NOTHING
 * else - {@code status}/{@code amount}/{@code attempts} are bit-for-bit identical before and after,
 * and no {@code PspAdapter} method is invoked.
 */
@Component
public class MarkPaymentDisputedCommandHandler implements CommandHandler<MarkPaymentDisputedCommand, Unit> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkPaymentDisputedCommandHandler.class);

    private final PaymentRepository paymentRepository;

    public MarkPaymentDisputedCommandHandler(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public Unit handle(MarkPaymentDisputedCommand command) {
        Payment payment = paymentRepository.findById(command.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Payment not found: " + command.paymentId(),
                        Map.of("paymentId", command.paymentId())));

        payment.markDisputed();
        paymentRepository.save(payment);

        LOGGER.info("Payment marked disputed paymentId={}", command.paymentId());
        return Unit.INSTANCE;
    }
}
