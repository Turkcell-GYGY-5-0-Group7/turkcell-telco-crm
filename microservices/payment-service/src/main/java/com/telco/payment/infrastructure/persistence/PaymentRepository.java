package com.telco.payment.infrastructure.persistence;

import com.telco.payment.domain.Payment;
import com.telco.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for the {@link Payment} aggregate. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByPaymentRequestId(String paymentRequestId);

    /**
     * Returns PENDING payments created between {@code minAge} (exclusive) and {@code maxAge}
     * (exclusive). Used by the retry scheduler to find payments whose PSP call was skipped because
     * the circuit breaker was open, but are not yet past the final retry window.
     *
     * @param minAge  now() minus 1 hour - skip brand-new payments
     * @param maxAge  now() minus 168 hours - drop payments past the final window
     */
    // disputed = false excludes payments under an open dispute from retry/expiry (ADR-028 Section 5) -
    // the dispute hold suppresses auto-retry/auto-settlement while under review.
    @Query("SELECT p FROM Payment p WHERE p.status = :status " +
           "AND p.createdAt < :minAge AND p.createdAt > :maxAge AND p.disputed = false")
    List<Payment> findByStatusAndCreatedAtBetween(
            @Param("status") PaymentStatus status,
            @Param("minAge") Instant minAge,
            @Param("maxAge") Instant maxAge);

    /**
     * Returns FAILED, non-disputed payments with fewer than {@code maxAttempts} attempts that are
     * still within the 168-hour retry window. The {@code SIZE(p.attempts)} expression counts the
     * cascade collection.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' " +
           "AND p.createdAt > :maxAge AND SIZE(p.attempts) < :maxAttempts AND p.disputed = false")
    List<Payment> findFailedForRetry(
            @Param("maxAge") Instant maxAge,
            @Param("maxAttempts") int maxAttempts);
}
