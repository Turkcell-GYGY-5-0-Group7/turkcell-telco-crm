package com.telco.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A line item within an {@link Order}. Stores a snapshot of the catalog price at order-creation
 * time so the order total remains consistent even if the catalog price changes later (FR-09).
 *
 * <p>Since Sprint 24 Feature 24.2 (design-note D1) an item is either a {@link OrderItemType#TARIFF}
 * or an {@link OrderItemType#ADDON} line:
 * <ul>
 *   <li>TARIFF items carry the V5 tariff snapshot ({@code tariffId}/{@code tariffCode}/
 *       {@code tariffVersion}) - all enforced non-null by {@link #forTariff} even though the columns
 *       were relaxed for ADDON rows - plus the optional campaign discount snapshot. A PLAN_CHANGE
 *       order's single TARIFF item additionally carries {@code targetSubscriptionId}.</li>
 *   <li>ADDON items carry the catalog {@code productCode}, the addon's allowance snapshot
 *       ({@code allowanceDataMb}/{@code allowanceMinutes}/{@code allowanceSms}), and - for
 *       standalone ADDON orders only - the {@code targetSubscriptionId}; their tariff snapshot
 *       columns stay null ({@code tariffName} is reused as the generic product-name snapshot).
 *       ADDON items are never campaign-discounted (campaign eligibility is tariff-scoped,
 *       ADR-027).</li>
 * </ul>
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

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private OrderItemType itemType;

    @Column(name = "tariff_id")
    private UUID tariffId;

    @Column(name = "tariff_code", length = 64)
    private String tariffCode;

    @Column(name = "tariff_version")
    private Integer tariffVersion;

    @Column(name = "tariff_name", length = 255)
    private String tariffName;

    @Column(name = "product_code", length = 64)
    private String productCode;

    @Column(name = "addon_type", length = 20)
    private String addonType;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "target_subscription_id")
    private UUID targetSubscriptionId;

    @Column(name = "allowance_data_mb")
    private Long allowanceDataMb;

    @Column(name = "allowance_minutes")
    private Long allowanceMinutes;

    @Column(name = "allowance_sms")
    private Long allowanceSms;

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

    private OrderItem(UUID id, Order order, OrderItemType itemType, UUID tariffId, String tariffCode,
                      Integer tariffVersion, String tariffName, String productCode,
                      String addonType, String currency,
                      UUID targetSubscriptionId, Long allowanceDataMb, Long allowanceMinutes,
                      Long allowanceSms, BigDecimal unitPrice, int quantity,
                      UUID campaignId, String campaignCode) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = Objects.requireNonNull(order, "order");
        this.itemType = Objects.requireNonNull(itemType, "itemType");
        this.tariffId = tariffId;
        this.tariffCode = tariffCode;
        this.tariffVersion = tariffVersion;
        this.tariffName = tariffName;
        this.productCode = productCode;
        this.addonType = addonType;
        this.currency = currency;
        this.targetSubscriptionId = targetSubscriptionId;
        this.allowanceDataMb = allowanceDataMb;
        this.allowanceMinutes = allowanceMinutes;
        this.allowanceSms = allowanceSms;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.campaignId = campaignId;
        this.campaignCode = campaignCode;
    }

    /** Backward-compatible overload for tariff items priced with no campaign discount. */
    public static OrderItem create(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                   String tariffName, BigDecimal unitPrice, int quantity) {
        return forTariff(order, tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity,
                null, null, null);
    }

    /** Backward-compatible overload for tariff items with no {@code targetSubscriptionId}. */
    public static OrderItem create(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                   String tariffName, BigDecimal unitPrice, int quantity,
                                   UUID campaignId, String campaignCode) {
        return forTariff(order, tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity,
                campaignId, campaignCode, null);
    }

    /**
     * Creates a {@link OrderItemType#TARIFF} line: the full V5 tariff snapshot is mandatory.
     * {@code targetSubscriptionId} is non-null only on a PLAN_CHANGE order's single item.
     */
    public static OrderItem forTariff(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                      String tariffName, BigDecimal unitPrice, int quantity,
                                      UUID campaignId, String campaignCode, UUID targetSubscriptionId) {
        return new OrderItem(UUID.randomUUID(), order, OrderItemType.TARIFF,
                Objects.requireNonNull(tariffId, "tariffId"),
                Objects.requireNonNull(tariffCode, "tariffCode"),
                tariffVersion, tariffName, null, null, null, targetSubscriptionId,
                null, null, null, unitPrice, quantity, campaignId, campaignCode);
    }

    /**
     * Creates an {@link OrderItemType#ADDON} line from the catalog addon snapshot taken at
     * order-creation time (design-note D1): price, display name (stored in the generic
     * {@code tariffName} product-name column), catalog category, price currency and allowance
     * deltas. {@code targetSubscriptionId} is non-null for standalone ADDON orders and null for
     * addons bundled into a NEW_LINE order. The full snapshot lets {@code addon.purchased.v1} be
     * published at fulfillment time without a runtime catalog hop (Feature 24.3, design-note D3).
     */
    public static OrderItem forAddon(Order order, String productCode, String productName,
                                     String addonType, String currency,
                                     BigDecimal unitPrice, int quantity, UUID targetSubscriptionId,
                                     Long allowanceDataMb, Long allowanceMinutes, Long allowanceSms) {
        return new OrderItem(UUID.randomUUID(), order, OrderItemType.ADDON,
                null, null, null, productName,
                Objects.requireNonNull(productCode, "productCode"),
                addonType, currency,
                targetSubscriptionId, allowanceDataMb, allowanceMinutes, allowanceSms,
                unitPrice, quantity, null, null);
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public OrderItemType getItemType() {
        return itemType;
    }

    /** The tariff id for TARIFF items; {@code null} for ADDON items. */
    public UUID getTariffId() {
        return tariffId;
    }

    /** The tariff code for TARIFF items; {@code null} for ADDON items. */
    public String getTariffCode() {
        return tariffCode;
    }

    /** The tariff version for TARIFF items; {@code null} for ADDON items. */
    public Integer getTariffVersion() {
        return tariffVersion;
    }

    /** Generic product-name snapshot: the tariff name for TARIFF items, the addon name for ADDON items. */
    public String getTariffName() {
        return tariffName;
    }

    /** The catalog addon code for ADDON items; {@code null} for TARIFF items. */
    public String getProductCode() {
        return productCode;
    }

    /**
     * The catalog addon category (DATA, SMS, MINUTES, VAS) snapshotted at order-creation time;
     * {@code null} for TARIFF items and for ADDON rows created before V9 (Feature 24.3).
     */
    public String getAddonType() {
        return addonType;
    }

    /**
     * ISO-4217 currency of {@code unitPrice} snapshotted at order-creation time; {@code null} for
     * TARIFF items and for ADDON rows created before V9 (Feature 24.3).
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * The existing subscription this item targets: non-null on standalone ADDON items and on a
     * PLAN_CHANGE order's TARIFF item; {@code null} on NEW_LINE items.
     */
    public UUID getTargetSubscriptionId() {
        return targetSubscriptionId;
    }

    /** Snapshotted addon data allowance in MB; {@code null} for TARIFF items or non-data addons. */
    public Long getAllowanceDataMb() {
        return allowanceDataMb;
    }

    /** Snapshotted addon voice allowance in minutes; {@code null} for TARIFF items or non-voice addons. */
    public Long getAllowanceMinutes() {
        return allowanceMinutes;
    }

    /** Snapshotted addon SMS allowance; {@code null} for TARIFF items or non-SMS addons. */
    public Long getAllowanceSms() {
        return allowanceSms;
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
