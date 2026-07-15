package com.telco.campaign.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Campaign aggregate root (design-note.md Section 5, ADR-027 Decision Section 3).
 *
 * <p>State machine (design-note.md Section 5, Feature 21.2.1): {@code DRAFT -> ACTIVE -> PAUSED ->
 * EXPIRED -> CANCELLED}. {@link #activate()}, {@link #pause()}, {@link #cancel()}, and
 * {@link #expire()} enforce the transition rules; an illegal transition raises
 * {@link BusinessRuleException}. {@code version} backs the optimistic locking (and, for the
 * redemption-cap write path, the explicit pessimistic lock in
 * {@code CampaignRepository.findByIdForUpdate}) that 21.2.2's concurrent redemption-cap checks rely
 * on.
 *
 * <p>{@code applicableTariffCodes} stores admin-curated, opaque product-catalog tariff codes in a
 * normalized child table ({@code campaign_tariff_codes}) - never a copy of tariff pricing data
 * (ADR-027 Decision Section 3), keeping campaign-db isolated from product-catalog-db.
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "campaign_tariff_codes",
            joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "tariff_code", nullable = false, length = 50)
    private Set<String> applicableTariffCodes = new LinkedHashSet<>();

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status;

    @Column(name = "total_redemption_cap")
    private Integer totalRedemptionCap;

    @Column(name = "per_customer_redemption_cap", nullable = false)
    private int perCustomerRedemptionCap;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    /**
     * Defensive flag set by the {@code tariff.price-changed.v1} consumer (Feature 21.4.3, ADR-027
     * Decision Section 4) when this campaign is ACTIVE and references a tariff code whose price has
     * since changed - never auto-{@link #expire()}s the campaign, only surfaces the fact for admin
     * review (chosen behavior, documented in {@code docs/api-contracts/campaign-service.md}).
     */
    @Column(name = "stale_tariff_flag", nullable = false)
    private boolean staleTariffFlag;

    @Column(name = "stale_tariff_reason", length = 500)
    private String staleTariffReason;

    @Column(name = "stale_tariff_flagged_at")
    private Instant staleTariffFlaggedAt;

    /** For JPA only. */
    protected Campaign() {
    }

    public Campaign(UUID id, String code, String name, String description,
                     DiscountType discountType, BigDecimal discountValue,
                     Set<String> applicableTariffCodes, Instant validFrom, Instant validTo,
                     CampaignStatus status, Integer totalRedemptionCap, int perCustomerRedemptionCap,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.discountType = Objects.requireNonNull(discountType, "discountType");
        this.discountValue = Objects.requireNonNull(discountValue, "discountValue");
        this.applicableTariffCodes = applicableTariffCodes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(applicableTariffCodes);
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        this.validTo = Objects.requireNonNull(validTo, "validTo");
        this.status = Objects.requireNonNull(status, "status");
        this.totalRedemptionCap = totalRedemptionCap;
        this.perCustomerRedemptionCap = perCustomerRedemptionCap;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Factory that creates a new campaign in {@link CampaignStatus#DRAFT} state. Mirrors
     * {@code Tariff.create}'s {@code effectiveTo > effectiveFrom} invariant: {@code validTo} must be
     * after {@code validFrom}.
     */
    public static Campaign create(String code, String name, String description,
                                   DiscountType discountType, BigDecimal discountValue,
                                   Set<String> applicableTariffCodes, Instant validFrom, Instant validTo,
                                   Integer totalRedemptionCap, int perCustomerRedemptionCap) {
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new BusinessRuleException(
                    "validTo must be after validFrom for campaign code: " + code);
        }
        Instant now = Instant.now();
        return new Campaign(UUID.randomUUID(), code, name, description, discountType, discountValue,
                applicableTariffCodes, validFrom, validTo, CampaignStatus.DRAFT,
                totalRedemptionCap, perCustomerRedemptionCap, now, now);
    }

    /**
     * DRAFT/PAUSED -&gt; ACTIVE. Requires {@code validFrom}/{@code validTo} to be set and
     * {@code validTo} to be after {@code validFrom} (mirrors {@code Tariff.create}'s invariant, checked
     * again here since activation - not construction - is the point at which the window becomes
     * load-bearing for eligibility evaluation, 21.2.3).
     */
    public void activate() {
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.PAUSED) {
            throw new BusinessRuleException(
                    "Cannot activate campaign in status: " + status
                            + ". Only DRAFT or PAUSED campaigns may be activated.");
        }
        if (validFrom == null || validTo == null) {
            throw new BusinessRuleException(
                    "Cannot activate campaign without both validFrom and validTo set: " + code);
        }
        if (!validTo.isAfter(validFrom)) {
            throw new BusinessRuleException(
                    "validTo must be after validFrom for campaign code: " + code);
        }
        this.status = CampaignStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /** ACTIVE -&gt; PAUSED. */
    public void pause() {
        if (status != CampaignStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Cannot pause campaign in status: " + status + ". Only ACTIVE campaigns may be paused.");
        }
        this.status = CampaignStatus.PAUSED;
        this.updatedAt = Instant.now();
    }

    /** Any non-terminal state (DRAFT/ACTIVE/PAUSED) -&gt; CANCELLED. */
    public void cancel() {
        if (status == CampaignStatus.CANCELLED || status == CampaignStatus.EXPIRED) {
            throw new BusinessRuleException(
                    "Cannot cancel campaign in terminal status: " + status);
        }
        this.status = CampaignStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /**
     * ACTIVE/PAUSED -&gt; EXPIRED. Called both explicitly (future admin action) and defensively by
     * {@code CampaignEligibilityService} when {@code validTo} is observed to have passed during
     * eligibility evaluation (21.2.3), so a lapsed campaign does not linger ACTIVE.
     */
    public void expire() {
        if (status != CampaignStatus.ACTIVE && status != CampaignStatus.PAUSED) {
            throw new BusinessRuleException(
                    "Cannot expire campaign in status: " + status
                            + ". Only ACTIVE or PAUSED campaigns may expire.");
        }
        this.status = CampaignStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    /**
     * Sets the defensive stale-tariff flag (Feature 21.4.3): logged/admin-visible only, never mutates
     * {@code status} - the tariff-defensive consumers deliberately do not auto-{@link #expire()} a
     * campaign on a price change (see {@code docs/api-contracts/campaign-service.md} "Tariff-defensive
     * behavior"). Idempotent: re-flagging simply refreshes the reason/timestamp.
     */
    public void flagStaleTariffReference(String reason) {
        this.staleTariffFlag = true;
        this.staleTariffReason = Objects.requireNonNull(reason, "reason");
        this.staleTariffFlaggedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    /** Unmodifiable view of the tariff codes this campaign applies to. */
    public Set<String> getApplicableTariffCodes() {
        return Collections.unmodifiableSet(applicableTariffCodes);
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Integer getTotalRedemptionCap() {
        return totalRedemptionCap;
    }

    public int getPerCustomerRedemptionCap() {
        return perCustomerRedemptionCap;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public boolean isStaleTariffFlag() {
        return staleTariffFlag;
    }

    public String getStaleTariffReason() {
        return staleTariffReason;
    }

    public Instant getStaleTariffFlaggedAt() {
        return staleTariffFlaggedAt;
    }
}
