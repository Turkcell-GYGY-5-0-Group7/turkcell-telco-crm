package com.telco.subscription.domain;

import com.telco.platform.common.exception.BusinessRuleException;
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
 * Aggregate root for a subscription in the subscription bounded context (FR-14, FR-15).
 *
 * <p>The lifecycle state machine is enforced here, framework-independent:
 * <ul>
 *   <li>ACTIVE -> SUSPENDED ({@link #suspend()})</li>
 *   <li>SUSPENDED -> ACTIVE ({@link #reactivate()})</li>
 *   <li>ACTIVE or SUSPENDED -> TERMINATED ({@link #terminate()})</li>
 * </ul>
 * Any other transition raises {@link BusinessRuleException}. A customer may hold multiple ACTIVE
 * subscriptions; there is no uniqueness on {@code customerId} (FR-15). JPA annotations describe the
 * mapping only; business behavior lives in this class.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "msisdn", nullable = false, length = 20)
    private String msisdn;

    @Column(name = "tariff_code", nullable = false, length = 64)
    private String tariffCode;

    @Column(name = "tariff_version", nullable = false)
    private int tariffVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Subscription() {
    }

    private Subscription(UUID id, UUID customerId, String msisdn, String tariffCode, int tariffVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.msisdn = Objects.requireNonNull(msisdn, "msisdn");
        this.tariffCode = Objects.requireNonNull(tariffCode, "tariffCode");
        this.tariffVersion = tariffVersion;
        this.status = SubscriptionStatus.ACTIVE;
        Instant now = Instant.now();
        this.activatedAt = now;
        this.createdAt = now;
    }

    /**
     * Activates a new subscription in {@link SubscriptionStatus#ACTIVE} state, bound to an allocated
     * MSISDN and the catalog offering version pinned at activation (FR-15).
     */
    public static Subscription activate(UUID customerId, String msisdn, String tariffCode, int tariffVersion) {
        return new Subscription(UUID.randomUUID(), customerId, msisdn, tariffCode, tariffVersion);
    }

    /**
     * Changes the tariff on an ACTIVE subscription (FR-09, plan-change order). Returns the previous
     * tariff code for the {@code subscription.tariff-changed.v1} event. The tariff-version snapshot
     * is left untouched: it pins the catalog version at activation; per-cycle pricing comes from
     * billing's tariff-price mirror keyed by code. Re-applying the same code is a harmless no-op
     * (inbox dedup is the first line of defense on redelivery).
     */
    public String changeTariff(String newTariffCode) {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot change tariff of subscription in status: " + this.status.name()
                            + ". Only ACTIVE subscriptions may change tariff.");
        }
        String oldTariffCode = this.tariffCode;
        this.tariffCode = Objects.requireNonNull(newTariffCode, "newTariffCode");
        return oldTariffCode;
    }

    /**
     * ACTIVE -> SUSPENDED. Suspending a subscription that is not ACTIVE raises
     * {@link BusinessRuleException}.
     */
    public void suspend() {
        if (this.status != SubscriptionStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot suspend subscription in status: " + this.status.name()
                            + ". Only ACTIVE subscriptions may be suspended.");
        }
        this.status = SubscriptionStatus.SUSPENDED;
    }

    /**
     * SUSPENDED -> ACTIVE. Reactivating a subscription that is not SUSPENDED raises
     * {@link BusinessRuleException}.
     */
    public void reactivate() {
        if (this.status != SubscriptionStatus.SUSPENDED) {
            throw new BusinessRuleException(
                    "Cannot reactivate subscription in status: " + this.status.name()
                            + ". Only SUSPENDED subscriptions may be reactivated.");
        }
        this.status = SubscriptionStatus.ACTIVE;
    }

    /**
     * ACTIVE or SUSPENDED -> TERMINATED. Terminating an already-TERMINATED subscription raises
     * {@link BusinessRuleException}.
     */
    public void terminate() {
        if (this.status == SubscriptionStatus.TERMINATED) {
            throw new BusinessRuleException(
                    "Cannot terminate subscription in status: " + this.status.name()
                            + ". Subscription is already terminated.");
        }
        this.status = SubscriptionStatus.TERMINATED;
        this.terminatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public int getTariffVersion() {
        return tariffVersion;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getTerminatedAt() {
        return terminatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
