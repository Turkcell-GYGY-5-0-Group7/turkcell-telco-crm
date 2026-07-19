package com.telco.dispute.domain;

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
 * Aggregate root for the dispute/chargeback workflow (ADR-028). Coordinates billing-service and
 * payment-service exclusively via outbox events - never writes to {@code billing-db} or
 * {@code payment-db} directly (ADR-006). See {@link DisputeStatus} for the full lifecycle.
 *
 * <p>State transitions are enforced here; JPA annotations describe the mapping only. Framework-free
 * by design (no Spring imports) - the caller (a command handler) resolves {@code changedBy} from
 * {@code UserContextHolder} and passes it in as a plain parameter.
 */
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    private UUID id;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DisputeStatus status;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    @Column(name = "disputed_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal disputedAmount;

    @Column(name = "resolution_amount", precision = 19, scale = 2)
    private BigDecimal resolutionAmount;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL)
    private List<DisputeEvidence> evidence = new ArrayList<>();

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL)
    private List<DisputeStateHistory> history = new ArrayList<>();

    /** For JPA only. */
    protected Dispute() {
    }

    private Dispute(UUID id, UUID invoiceId, UUID paymentId, UUID customerId, String reasonCode,
                    BigDecimal disputedAmount) {
        this.id = Objects.requireNonNull(id, "id");
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.disputedAmount = Objects.requireNonNull(disputedAmount, "disputedAmount");
        this.status = DisputeStatus.OPENED;
        this.openedAt = Instant.now();
    }

    /**
     * Creates a new dispute in {@link DisputeStatus#OPENED} state. At least one of
     * {@code invoiceId}/{@code paymentId} must be set (design-note.md Section 4) - a dispute with
     * neither is not attributable to any system of record.
     */
    public static Dispute create(UUID invoiceId, UUID paymentId, UUID customerId, String reasonCode,
                                 BigDecimal disputedAmount) {
        if (invoiceId == null && paymentId == null) {
            throw new BusinessRuleException(
                    "Cannot open a dispute with neither invoiceId nor paymentId set.");
        }
        return new Dispute(UUID.randomUUID(), invoiceId, paymentId, customerId, reasonCode, disputedAmount);
    }

    /**
     * Transitions to {@link DisputeStatus#UNDER_REVIEW}. Legal from {@code OPENED} (the initial
     * entry into review) or {@code EVIDENCE_SUBMITTED} (the loop-back after more evidence is
     * submitted, per ADR-028 Section 4 - review may resume any number of times).
     */
    public void beginReview(String changedBy) {
        if (this.status != DisputeStatus.OPENED && this.status != DisputeStatus.EVIDENCE_SUBMITTED) {
            throw new BusinessRuleException(
                    "Cannot begin review for dispute in status: " + this.status.name()
                            + ". Only OPENED or EVIDENCE_SUBMITTED disputes may enter review.");
        }
        transitionTo(DisputeStatus.UNDER_REVIEW, changedBy, null);
    }

    /** Transitions to {@link DisputeStatus#EVIDENCE_SUBMITTED}. Legal only from {@code UNDER_REVIEW}. */
    public void submitEvidence(String changedBy, String note) {
        if (this.status != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Cannot submit evidence for dispute in status: " + this.status.name()
                            + ". Only UNDER_REVIEW disputes may receive evidence.");
        }
        transitionTo(DisputeStatus.EVIDENCE_SUBMITTED, changedBy, note);
    }

    /**
     * Transitions to {@link DisputeStatus#RESOLVED_CUSTOMER} (dispute upheld: a real credit/refund
     * will be issued downstream). Legal only from {@code UNDER_REVIEW}. Requires a non-null,
     * strictly positive {@code resolutionAmount}.
     */
    public void resolveCustomer(BigDecimal resolutionAmount, String changedBy) {
        if (this.status != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Cannot resolve (customer) dispute in status: " + this.status.name()
                            + ". Only UNDER_REVIEW disputes may be resolved.");
        }
        if (resolutionAmount == null || resolutionAmount.signum() <= 0) {
            throw new BusinessRuleException(
                    "resolutionAmount must be a positive amount for a customer-favored resolution.");
        }
        this.resolutionAmount = resolutionAmount;
        transitionTo(DisputeStatus.RESOLVED_CUSTOMER, changedBy, null);
    }

    /**
     * Transitions to {@link DisputeStatus#RESOLVED_MERCHANT} (dispute rejected: no financial
     * change, per ADR-028 Section 5). Legal only from {@code UNDER_REVIEW}.
     */
    public void resolveMerchant(String changedBy) {
        if (this.status != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Cannot resolve (merchant) dispute in status: " + this.status.name()
                            + ". Only UNDER_REVIEW disputes may be resolved.");
        }
        transitionTo(DisputeStatus.RESOLVED_MERCHANT, changedBy, null);
    }

    /**
     * Transitions to {@link DisputeStatus#WITHDRAWN}. Legal from {@code OPENED} or
     * {@code UNDER_REVIEW} (the customer withdraws before or during review).
     */
    public void withdraw(String changedBy) {
        if (this.status != DisputeStatus.OPENED && this.status != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Cannot withdraw dispute in status: " + this.status.name()
                            + ". Only OPENED or UNDER_REVIEW disputes may be withdrawn.");
        }
        transitionTo(DisputeStatus.WITHDRAWN, changedBy, null);
    }

    /**
     * Transitions to the terminal {@link DisputeStatus#CLOSED} state. Legal from
     * {@code RESOLVED_CUSTOMER}, {@code RESOLVED_MERCHANT}, or {@code WITHDRAWN} - once the
     * resolution's downstream action (credit/refund/no-op) is confirmed.
     */
    public void close(String changedBy) {
        if (this.status != DisputeStatus.RESOLVED_CUSTOMER
                && this.status != DisputeStatus.RESOLVED_MERCHANT
                && this.status != DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleException(
                    "Cannot close dispute in status: " + this.status.name()
                            + ". Only RESOLVED_CUSTOMER, RESOLVED_MERCHANT, or WITHDRAWN disputes may be closed.");
        }
        transitionTo(DisputeStatus.CLOSED, changedBy, null);
    }

    private void transitionTo(DisputeStatus newStatus, String changedBy, String note) {
        DisputeStatus previous = this.status;
        this.status = newStatus;
        Instant now = Instant.now();
        if (newStatus == DisputeStatus.RESOLVED_CUSTOMER || newStatus == DisputeStatus.RESOLVED_MERCHANT) {
            this.resolvedAt = now;
        }
        if (newStatus == DisputeStatus.CLOSED) {
            this.closedAt = now;
        }
        history.add(DisputeStateHistory.create(this, previous, newStatus, changedBy, note));
    }

    /** Attaches an evidence record to this dispute. Callers use this after 22.3's upload step. */
    public DisputeEvidence addEvidence(String submittedBy, String objectRef) {
        DisputeEvidence item = DisputeEvidence.create(this, submittedBy, objectRef);
        evidence.add(item);
        return item;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public BigDecimal getDisputedAmount() {
        return disputedAmount;
    }

    public BigDecimal getResolutionAmount() {
        return resolutionAmount;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    /** Unmodifiable view of attached evidence. */
    public List<DisputeEvidence> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    /** Unmodifiable view of the state transition history. */
    public List<DisputeStateHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
