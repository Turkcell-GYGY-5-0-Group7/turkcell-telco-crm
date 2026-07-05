package com.telco.payment.application.scheduler;

import com.telco.payment.application.command.ChargePaymentCommand;
import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import com.telco.payment.infrastructure.persistence.PaymentRepository;
import com.telco.platform.mediator.Mediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Periodically retries PENDING and FAILED payments within their configured retry windows.
 *
 * <p>Retry windows (measured from {@code payment.created_at}):
 * <ul>
 *   <li>Window 1: 1h – 24h  — PENDING with 0 attempts (circuit-open scenario)</li>
 *   <li>Window 2: 24h – 72h — FAILED with 1 attempt</li>
 *   <li>Window 3: 72h – 168h — FAILED with 2 attempts</li>
 *   <li>After 168h or 3+ attempts: permanently FAILED, no further retries</li>
 * </ul>
 *
 * <p>Runs every hour ({@code fixedDelay = 3_600_000 ms}) after the previous run completes.
 * Uses the repository query parameters: {@code createdAt < minAge AND createdAt > maxAge}
 * where {@code minAge} is the upper-bound cutoff and {@code maxAge} is the lower-bound cutoff.
 */
@Component
public class PaymentRetryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRetryScheduler.class);

    /** Payments must be at least 1 hour old before the first retry (avoid retrying brand-new ones). */
    private static final long MIN_AGE_HOURS = 1L;
    private static final long WINDOW_2_START_HOURS = 24L;
    private static final long WINDOW_3_START_HOURS = 72L;
    private static final long MAX_AGE_HOURS = 168L;
    private static final int MAX_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final Mediator mediator;

    public PaymentRetryScheduler(PaymentRepository paymentRepository, Mediator mediator) {
        this.paymentRepository = paymentRepository;
        this.mediator = mediator;
    }

    @Scheduled(fixedDelay = 3_600_000L)
    public void retryPendingAndFailedPayments() {
        Instant now = Instant.now();

        expireStalePayments(now);
        retryPendingInAllWindows(now);
        retryFailedPayments(now);
    }

    /**
     * Marks PENDING payments older than 168 hours as permanently FAILED.
     * Query: {@code createdAt < (now - 168h) AND createdAt > Instant.EPOCH}.
     */
    private void expireStalePayments(Instant now) {
        // upper-bound = now - 168h; lower-bound = Instant.EPOCH (any time)
        Instant upperBound = now.minus(MAX_AGE_HOURS, ChronoUnit.HOURS);
        List<Payment> stale = paymentRepository.findByStatusAndCreatedAtBetween(
                PaymentStatus.PENDING, upperBound, Instant.EPOCH);

        for (Payment payment : stale) {
            LOGGER.info("Expiring stale PENDING payment id={} createdAt={} (past {}h window)",
                    payment.getId(), payment.getCreatedAt(), MAX_AGE_HOURS);
            payment.markPermanentlyFailed();
            paymentRepository.save(payment);
        }
    }

    /**
     * Retries PENDING payments (0 attempts) across all three retry windows.
     * Window 1: 1h – 24h; Window 2: 24h – 72h; Window 3: 72h – 168h.
     */
    private void retryPendingInAllWindows(Instant now) {
        // Window boundaries (all relative to now):
        // w1: createdAt in (now-24h, now-1h)  → older than 1h, newer than 24h
        // w2: createdAt in (now-72h, now-24h) → older than 24h, newer than 72h
        // w3: createdAt in (now-168h, now-72h)→ older than 72h, newer than 168h

        Instant cutoff1h   = now.minus(MIN_AGE_HOURS,          ChronoUnit.HOURS);
        Instant cutoff24h  = now.minus(WINDOW_2_START_HOURS,   ChronoUnit.HOURS);
        Instant cutoff72h  = now.minus(WINDOW_3_START_HOURS,   ChronoUnit.HOURS);
        Instant cutoff168h = now.minus(MAX_AGE_HOURS,          ChronoUnit.HOURS);

        // Query signature: findByStatusAndCreatedAtBetween(status, minAge, maxAge)
        // where JPQL is: createdAt < minAge AND createdAt > maxAge
        retry(paymentRepository.findByStatusAndCreatedAtBetween(
                PaymentStatus.PENDING, cutoff1h, cutoff24h), "pending-window-1");

        retry(paymentRepository.findByStatusAndCreatedAtBetween(
                PaymentStatus.PENDING, cutoff24h, cutoff72h), "pending-window-2");

        retry(paymentRepository.findByStatusAndCreatedAtBetween(
                PaymentStatus.PENDING, cutoff72h, cutoff168h), "pending-window-3");
    }

    /**
     * Retries FAILED payments with fewer than {@value #MAX_ATTEMPTS} attempts, in the
     * appropriate window based on attempt count.
     */
    private void retryFailedPayments(Instant now) {
        Instant maxAge = now.minus(MAX_AGE_HOURS, ChronoUnit.HOURS);
        List<Payment> failedPayments = paymentRepository.findFailedForRetry(maxAge, MAX_ATTEMPTS);

        for (Payment payment : failedPayments) {
            int attemptCount = payment.getAttempts().size();
            long ageHours = ChronoUnit.HOURS.between(payment.getCreatedAt(), now);

            boolean inWindow = switch (attemptCount) {
                // 1 failed attempt → retry in window 2 (24h-72h after creation)
                case 1 -> ageHours >= WINDOW_2_START_HOURS && ageHours < WINDOW_3_START_HOURS;
                // 2 failed attempts → retry in window 3 (72h-168h after creation)
                case 2 -> ageHours >= WINDOW_3_START_HOURS && ageHours < MAX_AGE_HOURS;
                default -> false;
            };

            if (inWindow) {
                issueRetry(payment, "failed-retry attempt=" + (attemptCount + 1));
            } else {
                LOGGER.debug("Payment id={} attempts={} ageHours={} not yet in retry window",
                        payment.getId(), attemptCount, ageHours);
            }
        }
    }

    private void retry(List<Payment> payments, String window) {
        for (Payment payment : payments) {
            issueRetry(payment, window);
        }
    }

    private void issueRetry(Payment payment, String window) {
        try {
            LOGGER.info("Scheduling retry for payment id={} orderId={} window={}",
                    payment.getId(), payment.getOrderId(), window);
            // Each retry must actually re-run the charge, so it carries a FRESH unique inbox key
            // (not the stable paymentRequestId, which would make the inbox short-circuit every retry
            // after the first as an already-seen delivery). Command-level idempotency via
            // paymentRequestId still re-uses the same Payment entity for the retry.
            String retryMessageId = "retry-" + payment.getId() + "-" + UUID.randomUUID();
            mediator.send(new ChargePaymentCommand(
                    payment.getOrderId(),
                    payment.getCustomerId(),
                    payment.getAmount(),
                    payment.getInvoiceId(),
                    payment.getPaymentRequestId(),
                    retryMessageId));
        } catch (Exception e) {
            // Non-fatal: next run will try again if still within the retry window.
            LOGGER.warn("Retry dispatch failed for payment id={}: {}", payment.getId(), e.getMessage());
        }
    }
}
