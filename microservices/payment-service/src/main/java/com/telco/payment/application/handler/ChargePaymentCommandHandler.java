package com.telco.payment.application.handler;

import com.telco.payment.application.AuditLogWriter;
import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.application.dto.PaymentResponse;
import com.telco.payment.application.event.PaymentCompletedEvent;
import com.telco.payment.application.event.PaymentFailedEvent;
import com.telco.payment.application.service.PaymentCreationService;
import com.telco.payment.domain.AttemptStatus;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentAttempt;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.payment.infrastructure.psp.ChargeResult;
import com.telco.payment.infrastructure.psp.PspAdapter;
import com.telco.payment.infrastructure.psp.PspException;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Handles {@link ChargePaymentCommand}: idempotent PSP charge with circuit-breaker protection.
 *
 * <p>Flow:
 * <ol>
 *   <li>If {@code paymentRequestId} maps to a terminal payment (COMPLETED/REFUNDED) return it.</li>
 *   <li>If it maps to a retryable payment (PENDING/FAILED) re-use that Payment and try PSP again.</li>
 *   <li>Otherwise create a new Payment (PENDING) via {@link PaymentCreationService} in REQUIRES_NEW
 *       so it survives a circuit-open rollback.</li>
 *   <li>Attempt the PSP charge through the circuit-breaker-wrapped {@link PspAdapter}.</li>
 *   <li>On success: COMPLETED + SUCCESS attempt + {@code payment.completed.v1} via outbox.</li>
 *   <li>On {@link PspException}: FAILED + FAILED attempt + {@code payment.failed.v1} via outbox.</li>
 *   <li>On {@link DependencyFailureException} (circuit OPEN): leave PENDING, rethrow (503).</li>
 * </ol>
 */
@Component
public class ChargePaymentCommandHandler
        implements CommandHandler<ChargePaymentCommand, PaymentResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargePaymentCommandHandler.class);
    private static final String OUTBOX_AGGREGATE_TYPE = "payment";
    private static final String AUDIT_ENTITY = "Payment";
    private static final String EVENT_COMPLETED = "payment.completed.v1";
    private static final String EVENT_FAILED = "payment.failed.v1";

    private final PaymentRepository paymentRepository;
    private final PaymentCreationService paymentCreationService;
    private final PspAdapter pspAdapter;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public ChargePaymentCommandHandler(PaymentRepository paymentRepository,
                                       PaymentCreationService paymentCreationService,
                                       PspAdapter pspAdapter,
                                       OutboxService outboxService,
                                       AuditLogWriter auditLogWriter) {
        this.paymentRepository = paymentRepository;
        this.paymentCreationService = paymentCreationService;
        this.pspAdapter = pspAdapter;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public PaymentResponse handle(ChargePaymentCommand command) {
        // Step 1: Idempotency check
        Optional<Payment> existing = paymentRepository.findByPaymentRequestId(command.paymentRequestId());

        Payment payment;
        if (existing.isPresent()) {
            Payment found = existing.get();
            // Terminal states: return as-is; do not re-charge.
            if (found.getStatus() == PaymentStatus.COMPLETED
                    || found.getStatus() == PaymentStatus.REFUNDED) {
                LOGGER.info("Idempotent return for paymentRequestId={} status={}",
                        command.paymentRequestId(), found.getStatus());
                return PaymentResponse.from(found);
            }
            // PENDING or FAILED: proceed with retry using the existing payment entity.
            LOGGER.info("Retrying PSP charge for paymentId={} status={} attempts={}",
                    found.getId(), found.getStatus(), found.getAttempts().size());
            payment = found;
        } else {
            // Step 2: New payment - persist in a separate TX so it survives circuit-open rollback.
            payment = Payment.create(
                    command.orderId(),
                    command.customerId(),
                    command.amount(),
                    command.paymentRequestId(),
                    command.invoiceId(),
                    command.method());
            paymentCreationService.saveNewPayment(payment);
            LOGGER.info("Created new payment id={} for orderId={}", payment.getId(), command.orderId());
        }

        int nextAttemptNumber = payment.getAttempts().size() + 1;

        // Step 3: Attempt PSP charge.
        try {
            ChargeResult result = pspAdapter.charge(
                    payment.getPaymentRequestId(),
                    payment.getAmount(),
                    payment.getCustomerId().toString());

            // Step 4a: Success
            payment.markCompleted();
            payment.addAttempt(
                    PaymentAttempt.create(payment, nextAttemptNumber, AttemptStatus.SUCCESS, null));
            paymentRepository.save(payment);

            auditLogWriter.log(
                    "PAYMENT_COMPLETED",
                    AUDIT_ENTITY,
                    payment.getId().toString(),
                    Map.of(
                            "orderId", payment.getOrderId().toString(),
                            "amount", payment.getAmount().toString()));

            outboxService.publish(
                    OUTBOX_AGGREGATE_TYPE, payment.getId().toString(), EVENT_COMPLETED,
                    new PaymentCompletedEvent(
                            payment.getId().toString(),
                            payment.getOrderId().toString(),
                            payment.getCustomerId().toString(),
                            payment.getAmount(),
                            payment.getInvoiceId() != null ? payment.getInvoiceId().toString() : null,
                            Instant.now().toString()));

            LOGGER.info("Payment completed paymentId={} transactionId={}",
                    payment.getId(), result.transactionId());
            return PaymentResponse.from(payment);

        } catch (PspException e) {
            // Step 5: Technical PSP failure - record attempt and mark FAILED.
            payment.markFailed();
            payment.addAttempt(
                    PaymentAttempt.create(payment, nextAttemptNumber, AttemptStatus.FAILED, e.getMessage()));
            paymentRepository.save(payment);

            auditLogWriter.log(
                    "PAYMENT_FAILED",
                    AUDIT_ENTITY,
                    payment.getId().toString(),
                    Map.of(
                            "orderId", payment.getOrderId().toString(),
                            "amount", payment.getAmount().toString(),
                            "reason", e.getMessage()));

            outboxService.publish(
                    OUTBOX_AGGREGATE_TYPE, payment.getId().toString(), EVENT_FAILED,
                    new PaymentFailedEvent(
                            payment.getId().toString(),
                            payment.getOrderId().toString(),
                            payment.getCustomerId().toString(),
                            payment.getAmount(),
                            payment.getInvoiceId() != null ? payment.getInvoiceId().toString() : null,
                            e.getMessage(),
                            Instant.now().toString()));

            LOGGER.warn("Payment failed paymentId={} reason={}", payment.getId(), e.getMessage());
            return PaymentResponse.from(payment);

        } catch (DependencyFailureException e) {
            // Step 6: Circuit OPEN - payment stays PENDING; retry scheduler will pick it up.
            LOGGER.warn("PSP circuit open for paymentId={} - keeping PENDING for retry",
                    payment.getId());
            throw e;
        }
    }
}
