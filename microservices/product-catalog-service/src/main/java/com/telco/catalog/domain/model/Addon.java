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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(mappedBy = "addons")
    private Set<Tariff> tariffs;

    /** For JPA only. */
    protected Addon() {
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
