package com.telco.billing.infrastructure.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tariff_prices")
public class TariffPrice {

    @Id
    private UUID id;

    @Column(name = "tariff_code", nullable = false, unique = true)
    private String tariffCode;

    @Column(name = "monthly_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyFee;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TariffPrice() {}

    public static TariffPrice of(String tariffCode, BigDecimal monthlyFee,
                                 String currency, Instant effectiveFrom) {
        TariffPrice tp = new TariffPrice();
        tp.id = UUID.randomUUID();
        tp.tariffCode = tariffCode;
        tp.monthlyFee = monthlyFee;
        tp.currency = currency;
        tp.effectiveFrom = effectiveFrom;
        tp.updatedAt = Instant.now();
        return tp;
    }

    public void update(BigDecimal monthlyFee, Instant effectiveFrom) {
        this.monthlyFee = monthlyFee;
        this.effectiveFrom = effectiveFrom;
        this.updatedAt = Instant.now();
    }

    public UUID getId()             { return id; }
    public String getTariffCode()   { return tariffCode; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public String getCurrency()     { return currency; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getUpdatedAt()   { return updatedAt; }
}
