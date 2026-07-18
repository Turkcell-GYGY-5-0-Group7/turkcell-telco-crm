package com.telco.fraud.domain;

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
 * Raw ingested event-log row (design-note.md Section 6). One row per consumed subscription-service
 * lifecycle event; the rolling-window rule queries (Feature 23.2) run against this table, which is
 * pruned periodically. fraud-service is read-only relative to subscription-service (ADR-029
 * Section 1) - these rows are built only from events consumed via the inbox, never by reading
 * {@code subscription-db}.
 *
 * <p>{@code customerId}, {@code msisdn}, and {@code subscriptionId} are nullable because not every
 * event type carries all three, and {@code msisdn.released.v1} historically lacked {@code customerId}
 * (ADR-029 Amendment 1, resolved defensively in 23.2). Bare JPA mapping only as of Feature 23.1 - no
 * domain behavior methods yet.
 */
@Entity
@Table(name = "msisdn_lifecycle_signal")
public class MsisdnLifecycleSignal {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private MsisdnLifecycleEventType eventType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "msisdn", length = 20)
    private String msisdn;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    /**
     * Suspension reason carried by {@code subscription.suspended.v1} (e.g. {@code NON_PAYMENT});
     * {@code null} for every other event type. Persisted (ADR-029 Amendment 1/3, Feature 23.2.4) so
     * {@code SUSPEND_REACTIVATE_VELOCITY} can exclude {@code NON_PAYMENT} suspensions from its
     * rolling-window count of historical rows.
     */
    @Column(name = "reason", length = 40)
    private String reason;

    /** For JPA only. */
    protected MsisdnLifecycleSignal() {
    }

    public MsisdnLifecycleSignal(UUID id, MsisdnLifecycleEventType eventType, UUID customerId,
                                 String msisdn, UUID subscriptionId, Instant occurredAt,
                                 Instant ingestedAt, String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.customerId = customerId;
        this.msisdn = msisdn;
        this.subscriptionId = subscriptionId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.ingestedAt = Objects.requireNonNull(ingestedAt, "ingestedAt");
        this.reason = reason;
    }

    public UUID getId() {
        return id;
    }

    public MsisdnLifecycleEventType getEventType() {
        return eventType;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public String getReason() {
        return reason;
    }
}
