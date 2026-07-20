package com.telco.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An addon attached to a subscription via an ADDON order (FR-09). Snapshot of the addon's price at
 * attach time; the fee bills on the next monthly invoice (FR-22, billing-service side). Immutable.
 */
@Entity
@Table(name = "subscription_addons")
public class SubscriptionAddon {

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

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "attached_at", nullable = false)
    private Instant attachedAt;

    /** For JPA only. */
    protected SubscriptionAddon() {
    }

    public static SubscriptionAddon attach(UUID subscriptionId, UUID orderId, String addonCode,
                                           String addonType, BigDecimal price, String currency) {
        SubscriptionAddon addon = new SubscriptionAddon();
        addon.id = UUID.randomUUID();
        addon.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        addon.orderId = Objects.requireNonNull(orderId, "orderId");
        addon.addonCode = Objects.requireNonNull(addonCode, "addonCode");
        addon.addonType = addonType;
        addon.price = Objects.requireNonNull(price, "price");
        addon.currency = Objects.requireNonNull(currency, "currency");
        addon.attachedAt = Instant.now();
        return addon;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getAddonCode() {
        return addonCode;
    }

    public String getAddonType() {
        return addonType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }
}
