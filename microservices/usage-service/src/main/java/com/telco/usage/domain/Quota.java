package com.telco.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for a subscription's usage quota in a billing period.
 *
 * <p>Domain logic lives here; no Spring dependencies. JPA annotations describe the mapping only.
 * State transitions are enforced through {@link #decrement(UsageType, long)}, which atomically
 * decrements the relevant remaining bucket and tracks threshold/exceeded notifications so
 * events are emitted exactly once per period.
 */
@Entity
@Table(name = "quotas")
public class Quota {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "minutes_total", nullable = false)
    private long minutesTotal;

    @Column(name = "sms_total", nullable = false)
    private long smsTotal;

    @Column(name = "mb_total", nullable = false)
    private long mbTotal;

    @Column(name = "minutes_remaining", nullable = false)
    private long minutesRemaining;

    @Column(name = "sms_remaining", nullable = false)
    private long smsRemaining;

    @Column(name = "mb_remaining", nullable = false)
    private long mbRemaining;

    @Column(name = "threshold_notified", nullable = false)
    private boolean thresholdNotified;

    @Column(name = "exceeded_notified", nullable = false)
    private boolean exceededNotified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** For JPA only. */
    protected Quota() {
    }

    private Quota(UUID id, UUID subscriptionId, UUID customerId,
                  Instant periodStart, Instant periodEnd,
                  long minutesTotal, long smsTotal, long mbTotal) {
        this.id = Objects.requireNonNull(id, "id");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.customerId = customerId;
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        this.minutesTotal = minutesTotal;
        this.smsTotal = smsTotal;
        this.mbTotal = mbTotal;
        this.minutesRemaining = minutesTotal;
        this.smsRemaining = smsTotal;
        this.mbRemaining = mbTotal;
        this.thresholdNotified = false;
        this.exceededNotified = false;
        this.createdAt = Instant.now();
    }

    /**
     * Factory: creates a new quota for a subscription's billing period.
     *
     * @param subscriptionId the subscription this quota covers
     * @param customerId     the customer who owns the subscription (used for ownership checks)
     * @param periodStart    inclusive start of the billing period
     * @param periodEnd      exclusive end of the billing period
     * @param minutesTotal   total voice minutes allowance
     * @param smsTotal       total SMS count allowance
     * @param mbTotal        total data megabyte allowance
     */
    public static Quota create(UUID subscriptionId, UUID customerId,
                               Instant periodStart, Instant periodEnd,
                               long minutesTotal, long smsTotal, long mbTotal) {
        return new Quota(UUID.randomUUID(), subscriptionId, customerId,
                periodStart, periodEnd, minutesTotal, smsTotal, mbTotal);
    }

    /**
     * Atomically decrements the relevant remaining bucket by the given quantity.
     *
     * <p>The decrement cannot go below zero; excess becomes overage. After decrement:
     * <ul>
     *   <li>If remaining drops to 20% or less of total and threshold has not been notified yet,
     *       {@code thresholdCrossed=true} is returned and {@code thresholdNotified} is flipped.</li>
     *   <li>If remaining reaches 0 and exceeded has not been notified yet,
     *       {@code exceededCrossed=true} is returned and {@code exceededNotified} is flipped.</li>
     * </ul>
     *
     * @param type     which bucket to decrement
     * @param quantity units to consume (seconds for VOICE, count for SMS, kilobytes for DATA)
     * @return a value object describing the outcome of the decrement
     */
    public DecrementResult decrement(UsageType type, long quantity) {
        long remaining;
        long total;

        switch (type) {
            case VOICE -> {
                remaining = minutesRemaining;
                total = minutesTotal;
            }
            case SMS -> {
                remaining = smsRemaining;
                total = smsTotal;
            }
            case DATA -> {
                remaining = mbRemaining;
                total = mbTotal;
            }
            default -> throw new IllegalArgumentException("Unknown usage type: " + type);
        }

        long consumed = Math.min(quantity, remaining);
        long overageQuantity = quantity - consumed;
        boolean overage = overageQuantity > 0;
        long newRemaining = remaining - consumed;

        switch (type) {
            case VOICE -> minutesRemaining = newRemaining;
            case SMS -> smsRemaining = newRemaining;
            case DATA -> mbRemaining = newRemaining;
        }

        this.updatedAt = Instant.now();

        boolean thresholdCrossed = false;
        boolean exceededCrossed = false;

        if (total > 0 && newRemaining <= (total / 5) && !thresholdNotified) {
            thresholdNotified = true;
            thresholdCrossed = true;
        }

        if (newRemaining == 0 && !exceededNotified) {
            exceededNotified = true;
            exceededCrossed = true;
        }

        return new DecrementResult(overage, overageQuantity, thresholdCrossed, exceededCrossed);
    }

    /**
     * Adds purchased addon allowances to this quota (Sprint 24 Feature 24.3, design-note D4).
     *
     * <p>Raises the totals AND the remaining balances by the given deltas, then re-arms the
     * notification flags for buckets that are back above their limits: {@code thresholdNotified}
     * is cleared when every in-use bucket is back above its 20% line, and {@code exceededNotified}
     * when no in-use bucket is exhausted - so a subscriber who tops up can be warned again later
     * in the same period. A bucket with a zero total is not in use and is neutral to the check
     * (the flags are shared across buckets, so an unused bucket must not block re-arming).
     *
     * @param minutes voice minutes to add (0 for none)
     * @param sms     SMS count to add (0 for none)
     * @param mb      data megabytes to add (0 for none)
     */
    public void addAllowance(long minutes, long sms, long mb) {
        this.minutesTotal += minutes;
        this.minutesRemaining += minutes;
        this.smsTotal += sms;
        this.smsRemaining += sms;
        this.mbTotal += mb;
        this.mbRemaining += mb;
        this.updatedAt = Instant.now();

        boolean allAboveThreshold = aboveThreshold(minutesRemaining, minutesTotal)
                && aboveThreshold(smsRemaining, smsTotal)
                && aboveThreshold(mbRemaining, mbTotal);
        if (thresholdNotified && allAboveThreshold) {
            thresholdNotified = false;
        }

        boolean noneExhausted = (minutesTotal == 0 || minutesRemaining > 0)
                && (smsTotal == 0 || smsRemaining > 0)
                && (mbTotal == 0 || mbRemaining > 0);
        if (exceededNotified && noneExhausted) {
            exceededNotified = false;
        }
    }

    /** An unused bucket (total 0) is neutral; an in-use bucket must be above its 20% line. */
    private static boolean aboveThreshold(long remaining, long total) {
        return total == 0 || remaining > total / 5;
    }

    /**
     * Value object returned by {@link #decrement(UsageType, long)}.
     *
     * @param overage         true when the quantity exceeded the remaining allowance
     * @param overageQuantity units that fell into overage territory (0 when overage is false)
     * @param thresholdCrossed true if the 80% consumption threshold was crossed for the first time
     * @param exceededCrossed  true if quota was fully exhausted for the first time
     */
    public record DecrementResult(boolean overage, long overageQuantity,
                                  boolean thresholdCrossed, boolean exceededCrossed) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public long getMinutesTotal() {
        return minutesTotal;
    }

    public long getSmsTotal() {
        return smsTotal;
    }

    public long getMbTotal() {
        return mbTotal;
    }

    public long getMinutesRemaining() {
        return minutesRemaining;
    }

    public long getSmsRemaining() {
        return smsRemaining;
    }

    public long getMbRemaining() {
        return mbRemaining;
    }

    public boolean isThresholdNotified() {
        return thresholdNotified;
    }

    public boolean isExceededNotified() {
        return exceededNotified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
