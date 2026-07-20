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
 * An addon fee awaiting invoicing (FR-22), recorded from {@code subscription.addon-attached.v1}.
 * The bill-run adds one ADDON/VAS invoice line per unbilled charge and flips {@code billed}.
 */
@Entity
@Table(name = "addon_charges")
public class AddonCharge {

    @Id
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "addon_code", nullable = false, length = 50)
    private String addonCode;

    @Column(name = "addon_type", length = 20)
    private String addonType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "attached_at", nullable = false)
    private Instant attachedAt;

    @Column(nullable = false)
    private boolean billed = false;

    protected AddonCharge() {}

    public static AddonCharge of(UUID subscriptionId, UUID orderId, String addonCode,
                                 String addonType, BigDecimal price, String currency,
                                 Instant attachedAt) {
        AddonCharge c = new AddonCharge();
        c.id = UUID.randomUUID();
        c.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        c.orderId = Objects.requireNonNull(orderId, "orderId");
        c.addonCode = Objects.requireNonNull(addonCode, "addonCode");
        c.addonType = addonType;
        c.price = Objects.requireNonNull(price, "price");
        c.currency = Objects.requireNonNull(currency, "currency");
        c.attachedAt = Objects.requireNonNull(attachedAt, "attachedAt");
        return c;
    }

    public void markBilled() {
        this.billed = true;
    }

    public UUID getId()             { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getOrderId()        { return orderId; }
    public String getAddonCode()    { return addonCode; }
    public String getAddonType()    { return addonType; }
    public BigDecimal getPrice()    { return price; }
    public String getCurrency()     { return currency; }
    public Instant getAttachedAt()  { return attachedAt; }
    public boolean isBilled()       { return billed; }
}
