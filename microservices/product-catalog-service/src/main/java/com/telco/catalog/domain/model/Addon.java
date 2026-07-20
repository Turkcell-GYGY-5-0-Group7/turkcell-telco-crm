package com.telco.catalog.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An optional bundle that can be attached to one or more tariffs (FR-CAT-02).
 * Immutable after creation; status transitions via dedicated methods.
 */
@Entity
@Table(name = "addons")
public class Addon {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddonType type;

    @Column(name = "validity_days", nullable = false)
    private int validityDays;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "data_mb")
    private Long dataMb;

    @Column(name = "voice_minutes")
    private Long voiceMinutes;

    @Column(name = "sms_count")
    private Long smsCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(mappedBy = "addons")
    private Set<Tariff> tariffs;

    /** For JPA only. */
    protected Addon() {
    }

    /**
     * Factory that creates a new ACTIVE addon (FR-05). Tariff links are managed from the
     * {@link Tariff} side of the many-to-many. Allowance fields are nullable and type-dependent:
     * a DATA addon carries {@code dataMb}, a MINUTES addon {@code voiceMinutes}, an SMS addon
     * {@code smsCount}, and a VAS addon none.
     */
    public static Addon create(String code, String name, BigDecimal price, String currency,
                               AddonType type, int validityDays,
                               Long dataMb, Long voiceMinutes, Long smsCount) {
        if (price == null || price.signum() < 0) {
            throw new BusinessRuleException("Addon price must be zero or positive: " + code);
        }
        if (validityDays <= 0) {
            throw new BusinessRuleException("Addon validityDays must be positive: " + code);
        }
        Addon addon = new Addon();
        addon.id = UUID.randomUUID();
        addon.code = Objects.requireNonNull(code, "code");
        addon.name = Objects.requireNonNull(name, "name");
        addon.price = price;
        addon.currency = Objects.requireNonNull(currency, "currency");
        addon.type = Objects.requireNonNull(type, "type");
        addon.validityDays = validityDays;
        addon.status = "ACTIVE";
        addon.dataMb = dataMb;
        addon.voiceMinutes = voiceMinutes;
        addon.smsCount = smsCount;
        addon.createdAt = Instant.now();
        return addon;
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

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public AddonType getType() {
        return type;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public String getStatus() {
        return Objects.requireNonNullElse(status, "ACTIVE");
    }

    public Long getDataMb() {
        return dataMb;
    }

    public Long getVoiceMinutes() {
        return voiceMinutes;
    }

    public Long getSmsCount() {
        return smsCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
