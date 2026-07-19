package com.telco.billing.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "sub_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal subTotal;

    @Column(name = "tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal tax;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "pdf_ref")
    private String pdfRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 16)
    private InvoiceDisputeStatus disputeStatus = InvoiceDisputeStatus.NONE;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {}

    public static Invoice create(UUID customerId, UUID subscriptionId,
                                 Instant periodStart, Instant periodEnd,
                                 BigDecimal subTotal, BigDecimal tax,
                                 String currency, LocalDate dueDate) {
        BigDecimal grandTotal = subTotal.add(tax);
        Invoice inv = new Invoice();
        inv.id = UUID.randomUUID();
        inv.customerId = customerId;
        inv.subscriptionId = subscriptionId;
        inv.periodStart = periodStart;
        inv.periodEnd = periodEnd;
        inv.subTotal = subTotal;
        inv.tax = tax;
        inv.grandTotal = grandTotal;
        inv.currency = currency;
        inv.status = InvoiceStatus.DRAFT;
        inv.dueDate = dueDate;
        inv.createdAt = Instant.now();
        return inv;
    }

    public void issue() {
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Invoice can only be issued from DRAFT state, current=" + status);
        }
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markPaid() {
        if (status == InvoiceStatus.PAID) {
            return;
        }
        if (status == InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Cannot pay a DRAFT invoice");
        }
        this.status = InvoiceStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void markOverdue() {
        if (status != InvoiceStatus.ISSUED) {
            return;
        }
        this.status = InvoiceStatus.OVERDUE;
        this.updatedAt = Instant.now();
    }

    public void attachPdf(String ref) {
        this.pdfRef = ref;
        this.updatedAt = Instant.now();
    }

    /**
     * Places a provisional hold on this invoice (ADR-028 Section 5) - a hold flag only, never a
     * financial mutation. Idempotent: re-applying a hold that is already set is harmless, so this
     * is an unconditional flag flip, not a guarded state-machine transition.
     */
    public void placeOnDisputeHold() {
        this.disputeStatus = InvoiceDisputeStatus.ON_HOLD;
        this.updatedAt = Instant.now();
    }

    /** Clears a dispute hold with no financial change (ADR-028 Section 5, {@code RESOLVED_MERCHANT}). */
    public void clearDisputeHold() {
        this.disputeStatus = InvoiceDisputeStatus.NONE;
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a real credit adjustment for a {@code dispute.resolved-customer.v1} resolution on an
     * unpaid invoice (ADR-028 Section 5). Check-then-act guard, independent of inbox dedup: no-ops
     * silently (no line, no total change) unless {@code disputeStatus == ON_HOLD} - this is the
     * second line of defense against a duplicate adjustment if inbox dedup is ever bypassed (bug,
     * manual replay, DLQ redrive), per ADR-028 Section 5's ratified amendment. A thrown exception
     * here would be wrong: the caller is a Kafka consumer, and throwing would retry forever on a
     * legitimately-already-resolved invoice.
     */
    public void applyDisputeAdjustment(BigDecimal amount) {
        if (this.disputeStatus != InvoiceDisputeStatus.ON_HOLD) {
            return;
        }
        InvoiceLine.of(this, "Dispute Adjustment", BigDecimal.ONE, amount.negate(), InvoiceLineType.ADJUSTMENT);
        this.subTotal = this.subTotal.subtract(amount);
        this.grandTotal = this.grandTotal.subtract(amount);
        this.disputeStatus = InvoiceDisputeStatus.NONE;
        this.updatedAt = Instant.now();
    }

    public UUID getId()             { return id; }
    public UUID getCustomerId()     { return customerId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd()   { return periodEnd; }
    public BigDecimal getSubTotal() { return subTotal; }
    public BigDecimal getTax()      { return tax; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public String getCurrency()     { return currency; }
    public InvoiceStatus getStatus() { return status; }
    public LocalDate getDueDate()   { return dueDate; }
    public Instant getIssuedAt()    { return issuedAt; }
    public String getPdfRef()       { return pdfRef; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
    public InvoiceDisputeStatus getDisputeStatus() { return disputeStatus; }
    public List<InvoiceLine> getLines() { return Collections.unmodifiableList(lines); }

    void addLine(InvoiceLine line) { this.lines.add(line); }
}
