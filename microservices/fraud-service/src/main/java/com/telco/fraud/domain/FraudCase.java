package com.telco.fraud.domain;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One or more related {@link FraudSignal}s escalated into an actionable case (design-note.md
 * Section 6). {@code signalIds} references the {@link FraudSignal} rows grouped into the case, stored
 * as a native PostgreSQL {@code uuid[]} column ({@code signal_ids}). {@code resolvedAt}/
 * {@code resolvedBy} are nullable until an agent closes the case ({@code CONFIRMED}/{@code DISMISSED}).
 * Case-open publishing ({@code fraud.case-opened.v1}) and ticket-service integration are Feature
 * 23.4 work. Bare JPA mapping only as of Feature 23.1 - no domain behavior methods yet.
 */
@Entity
@Table(name = "fraud_case")
public class FraudCase {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FraudCaseStatus status;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "signal_ids", columnDefinition = "uuid[]")
    private List<UUID> signalIds = new ArrayList<>();

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    /** For JPA only. */
    protected FraudCase() {
    }

    public FraudCase(UUID id, UUID customerId, FraudCaseStatus status, List<UUID> signalIds,
                     Instant openedAt, Instant resolvedAt, String resolvedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.status = Objects.requireNonNull(status, "status");
        this.signalIds = signalIds == null ? new ArrayList<>() : new ArrayList<>(signalIds);
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public FraudCaseStatus getStatus() {
        return status;
    }

    /**
     * Attaches a {@link FraudSignal} to this case (Feature 23.2.5) when a further qualifying signal is
     * raised for a customer who already has an {@code OPEN}/{@code UNDER_REVIEW} case, rather than
     * opening a duplicate. Idempotent: a signal id already present is not added twice. This is purely a
     * detect-and-alert bookkeeping change - it never touches subscription state (ADR-029 Section 5).
     */
    public void attachSignal(UUID signalId) {
        if (signalId != null && !signalIds.contains(signalId)) {
            signalIds.add(signalId);
        }
    }

    /**
     * Resolves this case to a terminal outcome (Feature 23.3.2, ADR-029 Section 5). {@code outcome}
     * must be {@link FraudCaseStatus#CONFIRMED} or {@link FraudCaseStatus#DISMISSED} - the terminal
     * states an agent closes a case into; any other target is a programming error rejected here. A
     * case already in a terminal state cannot be re-resolved (mirrors campaign-service's illegal
     * state-transition rejection via {@link BusinessRuleException} -> HTTP 422).
     *
     * <p><strong>Detect-and-alert only (ADR-029 Section 5):</strong> resolving is a status change on
     * this {@code FraudCase} row alone. It never suspends, holds, or otherwise mutates a subscription;
     * any suspension is a deliberate, out-of-band agent action against subscription-service. This
     * method touches no other aggregate and calls no external service.
     */
    public void resolve(FraudCaseStatus outcome, String resolvedBy, Instant resolvedAt) {
        if (outcome != FraudCaseStatus.CONFIRMED && outcome != FraudCaseStatus.DISMISSED) {
            throw new BusinessRuleException(
                    "A fraud case can only be resolved to CONFIRMED or DISMISSED, not " + outcome);
        }
        if (this.status == FraudCaseStatus.CONFIRMED || this.status == FraudCaseStatus.DISMISSED) {
            throw new BusinessRuleException(
                    "Fraud case " + id + " is already resolved (" + this.status + ")");
        }
        this.status = outcome;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    /** Unmodifiable view of the {@link FraudSignal} ids grouped into this case. */
    public List<UUID> getSignalIds() {
        return Collections.unmodifiableList(signalIds);
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }
}
