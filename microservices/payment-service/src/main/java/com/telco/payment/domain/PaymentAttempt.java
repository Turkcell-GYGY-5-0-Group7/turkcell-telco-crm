package com.telco.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Records one PSP charge attempt for a {@link Payment}. Immutable after creation; no state
 * transitions. Persisted via the {@link Payment} aggregate's cascade ({@code CascadeType.ALL}).
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttemptStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    /** For JPA only. */
    protected PaymentAttempt() {
    }

    private PaymentAttempt(UUID id, Payment payment, int attemptNumber,
                           AttemptStatus status, String errorMessage) {
        this.id = Objects.requireNonNull(id, "id");
        this.payment = Objects.requireNonNull(payment, "payment");
        this.attemptNumber = attemptNumber;
        this.status = Objects.requireNonNull(status, "status");
        this.errorMessage = errorMessage;
        this.attemptedAt = Instant.now();
    }

    /**
     * Factory for a new attempt record.
     *
     * @param payment       the owning payment aggregate
     * @param attemptNumber 1-based sequence number
     * @param status        outcome of this attempt
     * @param errorMessage  PSP error detail; null for successful attempts
     */
    public static PaymentAttempt create(Payment payment, int attemptNumber,
                                        AttemptStatus status, String errorMessage) {
        return new PaymentAttempt(UUID.randomUUID(), payment, attemptNumber, status, errorMessage);
    }

    public UUID getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
