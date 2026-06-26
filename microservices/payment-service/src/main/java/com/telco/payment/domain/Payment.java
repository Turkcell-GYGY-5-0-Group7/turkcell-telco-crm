package com.telco.payment.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for the payment bounded context (FR-25, FR-26, FR-27).
 *
 * <p>Domain logic lives here; no Spring dependencies. JPA annotations describe the mapping only.
 * State transitions are enforced via {@link #markCompleted()}, {@link #markFailed()}, and
 * {@link #markRefunded()} - callers never set the status field directly.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "payment_request_id", nullable = false, unique = true, length = 64)
    private String paymentRequestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAttempt> attempts = new ArrayList<>();

    /** For JPA only. */
    protected Payment() {
    }

    private Payment(UUID id, UUID orderId, UUID customerId, BigDecimal amount, String paymentRequestId) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.paymentRequestId = Objects.requireNonNull(paymentRequestId, "paymentRequestId");
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * Factory: creates a new payment in {@link PaymentStatus#PENDING} state.
     *
     * @param orderId          the order this payment covers
     * @param customerId       the paying customer
     * @param amount           the amount to charge (must be positive)
     * @param paymentRequestId idempotency key derived from the upstream command
     */
    public static Payment create(UUID orderId, UUID customerId, BigDecimal amount, String paymentRequestId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be positive");
        }
        return new Payment(UUID.randomUUID(), orderId, customerId, amount, paymentRequestId);
    }

    /** Transitions to {@link PaymentStatus#COMPLETED}. */
    public void markCompleted() {
        if (this.status == PaymentStatus.REFUNDED) {
            throw new BusinessRuleException("Cannot complete a refunded payment");
        }
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /** Transitions to {@link PaymentStatus#FAILED}. */
    public void markFailed() {
        if (this.status == PaymentStatus.COMPLETED || this.status == PaymentStatus.REFUNDED) {
            throw new BusinessRuleException(
                    "Cannot fail a payment in status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions from {@link PaymentStatus#COMPLETED} to {@link PaymentStatus#REFUNDED}.
     * Only completed payments can be refunded (business rule FR-27).
     */
    public void markRefunded() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Only COMPLETED payments can be refunded; current status: " + this.status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks this payment as permanently failed (after all retry windows exhausted).
     * Allowed from PENDING or FAILED states.
     */
    public void markPermanentlyFailed() {
        if (this.status == PaymentStatus.COMPLETED || this.status == PaymentStatus.REFUNDED) {
            throw new BusinessRuleException(
                    "Cannot permanently fail a payment in status: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    /** Appends a new attempt record. Cascade will persist it when Payment is saved. */
    public void addAttempt(PaymentAttempt attempt) {
        this.attempts.add(attempt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPaymentRequestId() {
        return paymentRequestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Unmodifiable view of all charge attempts for this payment. */
    public List<PaymentAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }
}
