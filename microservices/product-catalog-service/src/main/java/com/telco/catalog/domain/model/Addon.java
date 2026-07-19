package com.telco.catalog.domain.model;

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

    private Addon(UUID id, String code, String name, BigDecimal price, String currency,
                  AddonType type, int validityDays, Long dataMb, Long voiceMinutes, Long smsCount) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.price = Objects.requireNonNull(price, "price");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.type = Objects.requireNonNull(type, "type");
        this.validityDays = validityDays;
        this.status = "ACTIVE";
        this.dataMb = dataMb;
        this.voiceMinutes = voiceMinutes;
        this.smsCount = smsCount;
        this.createdAt = Instant.now();
    }

    /**
     * Factory that creates a new addon in {@code ACTIVE} status. Allowance fields are nullable:
     * a DATA addon carries {@code dataMb}, a MINUTES addon {@code voiceMinutes}, an SMS addon
     * {@code smsCount}, and a VAS addon none. The entity stays immutable after creation.
     */
    public static Addon create(String code, String name, BigDecimal price, String currency,
                               AddonType type, int validityDays,
                               Long dataMb, Long voiceMinutes, Long smsCount) {
        return new Addon(UUID.randomUUID(), code, name, price, currency, type, validityDays,
                dataMb, voiceMinutes, smsCount);
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
