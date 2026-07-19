package com.telco.billing.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-model row for one addon purchase (Sprint 24 Feature 24.3, design-note D3, FR-22),
 * populated from {@code addon.purchased.v1}.
 *
 * <p>{@code price} is the FULL purchase amount (event unit price multiplied by quantity - the
 * amount the order saga charged). The bill run adds exactly one invoice line per unbilled row and
 * calls {@link #markBilled(UUID)} in the same transaction, guaranteeing the line appears exactly
 * once on the recurring bill of record.
 */
@Entity
@Table(name = "addon_charge_records")
public class AddonChargeRecord {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "addon_code", nullable = false, length = 64)
    private String addonCode;

    @Column(name = "addon_name", length = 255)
    private String addonName;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    @Column(name = "billed", nullable = false)
    private boolean billed;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected AddonChargeRecord() {
    }

    private AddonChargeRecord(UUID id, UUID subscriptionId, UUID customerId, String addonCode,
                              String addonName, BigDecimal price, String currency,
                              Instant purchasedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.addonCode = Objects.requireNonNull(addonCode, "addonCode");
        this.addonName = addonName;
        this.price = Objects.requireNonNull(price, "price");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.purchasedAt = Objects.requireNonNull(purchasedAt, "purchasedAt");
        this.billed = false;
        this.createdAt = Instant.now();
    }

    /** Factory: records a new, not-yet-billed addon purchase. */
    public static AddonChargeRecord purchased(UUID subscriptionId, UUID customerId, String addonCode,
                                              String addonName, BigDecimal price, String currency,
                                              Instant purchasedAt) {
        return new AddonChargeRecord(UUID.randomUUID(), subscriptionId, customerId, addonCode,
                addonName, price, currency, purchasedAt);
    }

    /** Marks this charge as billed on the given invoice. Must run inside the bill-run transaction. */
    public void markBilled(UUID invoiceId) {
        this.billed = true;
        this.invoiceId = invoiceId;
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

    public String getAddonCode() {
        return addonCode;
    }

    public String getAddonName() {
        return addonName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public boolean isBilled() {
        return billed;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
