package com.telco.catalog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable price snapshot of a {@link Tariff} at a specific version (FR-CAT-03).
 *
 * <p>Created by {@link com.telco.catalog.domain.service.TariffVersioningService} every time a
 * tariff's price or included-unit attributes change. No setters: once persisted the snapshot
 * must never change (immutable audit trail).
 */
@Entity
@Table(name = "tariff_versions")
public class TariffVersion {

    @Id
    private UUID id;

    @Column(name = "tariff_code", nullable = false, length = 50)
    private String tariffCode;

    @Column(nullable = false)
    private int version;

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

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;

    /** For JPA only. */
    protected TariffVersion() {
    }

    /**
     * Creates an immutable snapshot from the current state of {@code tariff}.
     * The {@code snapshotAt} timestamp is set to the current clock time.
     */
    public static TariffVersion snapshot(Tariff tariff) {
        Objects.requireNonNull(tariff, "tariff");
        TariffVersion tv = new TariffVersion();
        tv.id = UUID.randomUUID();
        tv.tariffCode = tariff.getCode();
        tv.version = tariff.getVersion();
        tv.monthlyFee = tariff.getMonthlyFee();
        tv.currency = tariff.getCurrency();
        tv.minutesIncluded = tariff.getMinutesIncluded();
        tv.smsIncluded = tariff.getSmsIncluded();
        tv.dataMbIncluded = tariff.getDataMbIncluded();
        tv.effectiveFrom = tariff.getEffectiveFrom();
        tv.effectiveTo = tariff.getEffectiveTo();
        tv.snapshotAt = Instant.now();
        return tv;
    }

    public UUID getId() {
        return id;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public int getVersion() {
        return version;
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

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }
}
