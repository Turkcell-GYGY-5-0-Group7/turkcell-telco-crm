package com.telco.fraud.domain;

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
 * An evaluated rule hit (design-note.md Section 6). Produced by rule evaluation (Feature 23.2) when a
 * {@link FraudRule}'s threshold is met over its rolling window. {@code sourceSignalIds} references the
 * {@link MsisdnLifecycleSignal} rows that triggered it, stored as a native PostgreSQL {@code uuid[]}
 * column ({@code source_signal_ids}). {@code customerId}/{@code msisdn}/{@code subscriptionId} are
 * nullable because the observable identifiers vary by rule (ADR-029 Section 4, Amendments 1-2). Bare
 * JPA mapping only as of Feature 23.1 - no domain behavior methods yet.
 */
@Entity
@Table(name = "fraud_signal")
public class FraudSignal {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 40)
    private FraudRuleCode ruleCode;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "msisdn", length = 20)
    private String msisdn;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private FraudSeverity severity;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_signal_ids", columnDefinition = "uuid[]")
    private List<UUID> sourceSignalIds = new ArrayList<>();

    /** For JPA only. */
    protected FraudSignal() {
    }

    public FraudSignal(UUID id, FraudRuleCode ruleCode, UUID customerId, String msisdn,
                       UUID subscriptionId, FraudSeverity severity, Instant triggeredAt,
                       List<UUID> sourceSignalIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.ruleCode = Objects.requireNonNull(ruleCode, "ruleCode");
        this.customerId = customerId;
        this.msisdn = msisdn;
        this.subscriptionId = subscriptionId;
        this.severity = Objects.requireNonNull(severity, "severity");
        this.triggeredAt = Objects.requireNonNull(triggeredAt, "triggeredAt");
        this.sourceSignalIds = sourceSignalIds == null ? new ArrayList<>() : new ArrayList<>(sourceSignalIds);
    }

    public UUID getId() {
        return id;
    }

    public FraudRuleCode getRuleCode() {
        return ruleCode;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public FraudSeverity getSeverity() {
        return severity;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    /** Unmodifiable view of the source {@link MsisdnLifecycleSignal} ids that triggered this signal. */
    public List<UUID> getSourceSignalIds() {
        return Collections.unmodifiableList(sourceSignalIds);
    }
}
