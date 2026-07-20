package com.telco.catalog.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root representing a tariff plan in the product catalog (FR-CAT-01).
 *
 * <p>Framework-independent domain type: behavior lives here with no Spring dependency;
 * JPA annotations only describe the mapping. The {@link #create} factory enforces the
 * {@code effectiveTo > effectiveFrom} invariant at construction time.
 */
@Entity
@Table(name = "tariffs")
public class Tariff {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TariffType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TariffStatus status;

    @Column(name = "monthly_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyFee;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "minutes_included", nullable = false)
    private int minutesIncluded;

    @Column(name = "sms_included", nullable = false)
    private int smsIncluded;

    @Column(name = "data_mb_included", nullable = false)
    private int dataMbIncluded;

    @Column(name = "target_segment", length = 100)
    private String targetSegment;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tariff_addons",
            joinColumns = @JoinColumn(name = "tariff_id"),
            inverseJoinColumns = @JoinColumn(name = "addon_id"))
    private Set<Addon> addons = new LinkedHashSet<>();

    /** For JPA only. */
    protected Tariff() {
    }

    private Tariff(UUID id, String code, String name, TariffType type, TariffStatus status,
                   BigDecimal monthlyFee, String currency,
                   int minutesIncluded, int smsIncluded, int dataMbIncluded,
                   String targetSegment, Instant effectiveFrom, Instant effectiveTo) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.monthlyFee = Objects.requireNonNull(monthlyFee, "monthlyFee");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.minutesIncluded = minutesIncluded;
        this.smsIncluded = smsIncluded;
        this.dataMbIncluded = dataMbIncluded;
        this.targetSegment = targetSegment;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        this.effectiveTo = effectiveTo;
        this.version = 1;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Factory that creates a new tariff in {@link TariffStatus#DRAFT} state.
     * Enforces {@code effectiveTo > effectiveFrom} when {@code effectiveTo} is provided.
     */
    public static Tariff create(String code, String name, TariffType type,
                                BigDecimal monthlyFee, String currency,
                                int minutesIncluded, int smsIncluded, int dataMbIncluded,
                                String targetSegment, Instant effectiveFrom, Instant effectiveTo) {
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new BusinessRuleException(
                    "effectiveTo must be after effectiveFrom for tariff code: " + code);
        }
        return new Tariff(UUID.randomUUID(), code, name, type, TariffStatus.DRAFT,
                monthlyFee, currency, minutesIncluded, smsIncluded, dataMbIncluded,
                targetSegment, effectiveFrom, effectiveTo);
    }

    /**
     * Applies a price change and bumps the version counter. Called by
     * {@link com.telco.catalog.domain.service.TariffVersioningService}.
     */
    public void applyPriceChange(BigDecimal newMonthlyFee) {
        if (newMonthlyFee == null || newMonthlyFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Monthly fee must be non-negative");
        }
        this.monthlyFee = newMonthlyFee;
        this.version = this.version + 1;
        this.updatedAt = Instant.now();
    }

    /** Transitions this tariff to {@link TariffStatus#ACTIVE}. */
    public void activate() {
        this.status = TariffStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /** Retires this tariff. */
    public void retire() {
        this.status = TariffStatus.RETIRED;
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

    public TariffType getType() {
        return type;
    }

    public TariffStatus getStatus() {
        return status;
    }

    public BigDecimal getMonthlyFee() {
        return monthlyFee;
    }

    public String getCurrency() {
        return currency;
    }

    public int getMinutesIncluded() {
        return minutesIncluded;
    }

    public int getSmsIncluded() {
        return smsIncluded;
    }

    public int getDataMbIncluded() {
        return dataMbIncluded;
    }

    public String getTargetSegment() {
        return targetSegment;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Unmodifiable view of the addons bundled with this tariff. */
    public Set<Addon> getAddons() {
        return Collections.unmodifiableSet(addons);
    }

    /**
     * Links an addon to this tariff. The {@code tariff_addons} join table is owned by this side
     * of the many-to-many mapping, so join rows only persist when the addon is added here and the
     * tariff is saved. Idempotent for an already-linked addon (set semantics).
     */
    public void addAddon(Addon addon) {
        Objects.requireNonNull(addon, "addon");
        if (this.addons.add(addon)) {
            this.updatedAt = Instant.now();
        }
    }
}
