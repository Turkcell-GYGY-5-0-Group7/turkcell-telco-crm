package com.telco.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A line item within an {@link Order}. Stores a snapshot of the tariff price at order-creation
 * time so the order total remains consistent even if the catalog price changes later (FR-09).
 *
 * <p>{@code campaignId}/{@code campaignCode} are nullable, additive fields (Feature 21.3.3, ADR-027
 * Decision Section 4 third ratification addendum) recording which campaign, if any, discounted this
 * item's {@code unitPrice} at order-creation time - the same snapshot-symmetry pattern as
 * {@code tariff_id}/{@code tariff_code}/{@code tariff_version} (added in
 * {@code V5__add_tariff_snapshot_to_order_items.sql}). {@code campaignId} is always populated when a
 * discount applied (it is the correlation key Feature 21.4's redemption-confirmation flow needs);
 * {@code campaignCode} is populated only when the caller explicitly requested that campaign - when
 * campaign-service auto-resolved the best match (no {@code campaignCode} in the request), only the
 * id is known to order-service, which is sufficient for correlation.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "tariff_id", nullable = false)
    private UUID tariffId;

    @Column(name = "tariff_code", nullable = false, length = 64)
    private String tariffCode;

    @Column(name = "tariff_version", nullable = false)
    private int tariffVersion;

    @Column(name = "tariff_name", length = 255)
    private String tariffName;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "campaign_code", length = 50)
    private String campaignCode;

    /** For JPA only. */
    protected OrderItem() {
    }

    private OrderItem(UUID id, Order order, UUID tariffId, String tariffCode, int tariffVersion,
                      String tariffName, BigDecimal unitPrice, int quantity,
                      UUID campaignId, String campaignCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = Objects.requireNonNull(order, "order");
        this.tariffId = Objects.requireNonNull(tariffId, "tariffId");
        this.tariffCode = Objects.requireNonNull(tariffCode, "tariffCode");
        this.tariffVersion = tariffVersion;
        this.tariffName = tariffName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.campaignId = campaignId;
        this.campaignCode = campaignCode;
    }

    /** Backward-compatible overload for items priced with no campaign discount. */
    public static OrderItem create(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                   String tariffName, BigDecimal unitPrice, int quantity) {
        return create(order, tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity,
                null, null);
    }

    public static OrderItem create(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                   String tariffName, BigDecimal unitPrice, int quantity,
                                   UUID campaignId, String campaignCode) {
        return new OrderItem(UUID.randomUUID(), order, tariffId, tariffCode, tariffVersion,
                tariffName, unitPrice, quantity, campaignId, campaignCode);
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public UUID getTariffId() {
        return tariffId;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public int getTariffVersion() {
        return tariffVersion;
    }

    public String getTariffName() {
        return tariffName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    /** The campaign that discounted this item's {@code unitPrice}, or {@code null} if undiscounted. */
    public UUID getCampaignId() {
        return campaignId;
    }

    /**
     * The campaign code, if the caller explicitly requested it; {@code null} both when undiscounted
     * and when campaign-service auto-resolved the applied campaign (see class javadoc).
     */
    public String getCampaignCode() {
        return campaignCode;
    }
}
