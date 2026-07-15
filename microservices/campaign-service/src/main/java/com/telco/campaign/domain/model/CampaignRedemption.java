package com.telco.campaign.domain.model;

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
 * CampaignRedemption aggregate (design-note.md Section 5, ADR-027 Decision Section 4).
 *
 * <p>Lifecycle (Feature 21.2.2): {@link #reserve} creates a new row in {@link RedemptionStatus#RESERVED};
 * {@link #confirm()} transitions RESERVED -&gt; CONFIRMED; {@link #release()} transitions
 * RESERVED -&gt; RELEASED. Each transition validates the current status first and raises
 * {@link BusinessRuleException} on an illegal one. The actual event-driven triggers (consuming
 * {@code order.created.v1}, {@code payment.completed.v1}, {@code order.cancelled.v1}) are wired in
 * Feature 21.4 - this class only exposes the domain methods those consumers will call.
 *
 * <p>{@code orderId} is nullable until an order-correlation strategy is settled (see the open item
 * tracked in 21.3/21.4). {@code reservedUntil} bounds a RESERVED hold (ADR-027 Section 4 ratification
 * amendment) so a reservation-expiry reaper (Feature 21.4, mirroring {@code subscription-service}'s
 * MSISDN reaper) can release redemptions stranded by an abandoned order.
 */
@Entity
@Table(name = "campaign_redemptions")
public class CampaignRedemption {

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RedemptionStatus status;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    /** For JPA only. */
    protected CampaignRedemption() {
    }

    public CampaignRedemption(UUID id, UUID campaignId, UUID customerId, UUID orderId,
                               RedemptionStatus status, Instant redeemedAt, Instant confirmedAt,
                               Instant reservedUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.orderId = orderId;
        this.status = Objects.requireNonNull(status, "status");
        this.redeemedAt = Objects.requireNonNull(redeemedAt, "redeemedAt");
        this.confirmedAt = confirmedAt;
        this.reservedUntil = reservedUntil;
    }

    /**
     * Creates a new redemption hold in {@link RedemptionStatus#RESERVED}. This is the sole entry
     * point for bringing a {@code CampaignRedemption} into existence - there is no prior state to
     * transition from (contrast with {@code MsisdnPool}, whose rows pre-exist FREE). Rejects a
     * {@code reservedUntil} that is not strictly in the future.
     */
    public static CampaignRedemption reserve(UUID campaignId, UUID customerId, UUID orderId,
                                              Instant reservedUntil) {
        Instant now = Instant.now();
        if (reservedUntil != null && !reservedUntil.isAfter(now)) {
            throw new BusinessRuleException("reservedUntil must be in the future for a new reservation");
        }
        return new CampaignRedemption(UUID.randomUUID(), campaignId, customerId, orderId,
                RedemptionStatus.RESERVED, now, null, reservedUntil);
    }

    /** RESERVED -&gt; CONFIRMED. Set when the underlying order is confirmed real (21.4). */
    public void confirm() {
        if (status != RedemptionStatus.RESERVED) {
            throw new BusinessRuleException(
                    "Cannot confirm redemption in status: " + status
                            + ". Only RESERVED redemptions may be confirmed.");
        }
        this.status = RedemptionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
        this.reservedUntil = null;
    }

    /** RESERVED -&gt; RELEASED. Frees the cap slot back up (order cancelled or reservation expired). */
    public void release() {
        if (status != RedemptionStatus.RESERVED) {
            throw new BusinessRuleException(
                    "Cannot release redemption in status: " + status
                            + ". Only RESERVED redemptions may be released.");
        }
        this.status = RedemptionStatus.RELEASED;
        this.reservedUntil = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public RedemptionStatus getStatus() {
        return status;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }
}
