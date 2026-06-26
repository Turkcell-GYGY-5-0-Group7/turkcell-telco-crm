package com.telco.payment.application.service;

import com.telco.payment.domain.Payment;
import com.telco.payment.domain.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a brand-new {@link Payment} in an independent transaction
 * ({@link Propagation#REQUIRES_NEW}).
 *
 * <p>This design decouples the initial PENDING payment insert from the outer mediator
 * transaction: if the PSP call fails with the circuit breaker OPEN, the outer
 * {@code TransactionBehavior} rolls back its transaction, but the PENDING payment
 * row has already been committed here and is visible to the retry scheduler.
 *
 * <p>REQUIRES_NEW requires a Spring-managed proxy call (cannot be called as {@code this.method()}
 * from the same class), so this lives in a separate bean.
 */
@Service
public class PaymentCreationService {

    private final PaymentRepository paymentRepository;

    public PaymentCreationService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Persists {@code payment} in its own transaction that commits immediately, independent
     * of any surrounding transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment saveNewPayment(Payment payment) {
        return paymentRepository.save(payment);
    }
}
