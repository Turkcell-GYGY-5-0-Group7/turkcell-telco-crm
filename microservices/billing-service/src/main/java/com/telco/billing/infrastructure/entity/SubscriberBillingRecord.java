package com.telco.billing.infrastructure.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriber_billing_records")
public class SubscriberBillingRecord {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String TERMINATED = "TERMINATED";

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false, unique = true)
    private UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "tariff_code", nullable = false)
    private String tariffCode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriberBillingRecord() {}

    public static SubscriberBillingRecord activated(UUID subscriptionId, UUID customerId,
                                                    String tariffCode, Instant activatedAt) {
        SubscriberBillingRecord r = new SubscriberBillingRecord();
        r.id = UUID.randomUUID();
        r.subscriptionId = subscriptionId;
        r.customerId = customerId;
        r.tariffCode = tariffCode;
        r.status = ACTIVE;
        r.activatedAt = activatedAt;
        r.updatedAt = Instant.now();
        return r;
    }

    /**
     * Records a plan change so the next bill run charges the new tariff's fee (Sprint 24 Feature
     * 24.4, design-note D2).
     */
    public void changeTariff(String newTariffCode) {
        this.tariffCode = newTariffCode;
        this.updatedAt = Instant.now();
    }

    public void suspend(Instant at) {
        this.status = SUSPENDED;
        this.suspendedAt = at;
        this.updatedAt = Instant.now();
    }

    public void terminate(Instant at) {
        this.status = TERMINATED;
        this.terminatedAt = at;
        this.updatedAt = Instant.now();
    }

    public UUID getId()              { return id; }
    public UUID getSubscriptionId()  { return subscriptionId; }
    public UUID getCustomerId()      { return customerId; }
    public String getTariffCode()    { return tariffCode; }
    public String getStatus()        { return status; }
    public Instant getActivatedAt()  { return activatedAt; }
    public Instant getSuspendedAt()  { return suspendedAt; }
    public Instant getTerminatedAt() { return terminatedAt; }
    public Instant getUpdatedAt()    { return updatedAt; }
}
