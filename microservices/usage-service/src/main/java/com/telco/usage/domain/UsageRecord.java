package com.telco.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a single CDR application to a quota bucket.
 *
 * <p>No Spring dependencies. JPA annotations describe the mapping only.
 */
@Entity
@Table(name = "usage_records")
public class UsageRecord {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "quota_id")
    private UUID quotaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private UsageType type;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "overage", nullable = false)
    private boolean overage;

    @Column(name = "cdr_ref", nullable = false, unique = true, length = 128)
    private String cdrRef;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** For JPA only. */
    protected UsageRecord() {
    }

    private UsageRecord(UUID id, UUID subscriptionId, UUID quotaId, UsageType type,
                        long quantity, boolean overage, String cdrRef) {
        this.id = Objects.requireNonNull(id, "id");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.quotaId = quotaId;
        this.type = Objects.requireNonNull(type, "type");
        this.quantity = quantity;
        this.overage = overage;
        this.cdrRef = Objects.requireNonNull(cdrRef, "cdrRef");
        this.recordedAt = Instant.now();
    }

    /**
     * Factory: creates a new usage record for a CDR application.
     *
     * @param subscriptionId the subscription that consumed the usage
     * @param quotaId        the quota this CDR was applied to (null if no active quota was found)
     * @param type           usage type
     * @param quantity       units consumed
     * @param overage        true when quantity exceeded available allowance
     * @param cdrRef         unique CDR reference for idempotency
     */
    public static UsageRecord create(UUID subscriptionId, UUID quotaId, UsageType type,
                                     long quantity, boolean overage, String cdrRef) {
        return new UsageRecord(UUID.randomUUID(), subscriptionId, quotaId, type,
                quantity, overage, cdrRef);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getQuotaId() {
        return quotaId;
    }

    public UsageType getType() {
        return type;
    }

    public long getQuantity() {
        return quantity;
    }

    public boolean isOverage() {
        return overage;
    }

    public String getCdrRef() {
        return cdrRef;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
