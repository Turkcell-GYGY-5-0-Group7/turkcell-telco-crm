package com.telco.order.domain.model;

import com.telco.platform.common.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an order in the order orchestration bounded context (FR-09, FR-10).
 *
 * <p>State transitions are enforced here: only PENDING orders may be cancelled.
 * JPA annotations describe the mapping only; business behavior lives in this class.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /** For JPA only. */
    protected Order() {
    }

    private Order(UUID id, UUID customerId, String idempotencyKey, BigDecimal totalAmount,
                  String userId, OrderType orderType) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Backward-compatible overload creating a {@link OrderType#NEW_LINE} order - the only kind that
     * existed before Sprint 24 Feature 24.2.
     */
    public static Order create(UUID customerId, String idempotencyKey, BigDecimal totalAmount, String userId) {
        return create(customerId, idempotencyKey, totalAmount, userId, OrderType.NEW_LINE);
    }

    /**
     * Creates a new order in {@link OrderStatus#PENDING} state. {@code orderType} is derived from
     * the items by the caller ({@code CreateOrderCommandHandler}) and persisted so saga consumers
     * can branch without re-deriving (Sprint 24 design-note D1/D2).
     */
    public static Order create(UUID customerId, String idempotencyKey, BigDecimal totalAmount,
                               String userId, OrderType orderType) {
        return new Order(UUID.randomUUID(), customerId, idempotencyKey, totalAmount, userId, orderType);
    }

    /**
     * Adds a tariff item to this order with no campaign discount. Callers use this to build the item
     * list before persisting.
     */
    public OrderItem addItem(UUID tariffId, String tariffCode, int tariffVersion, String tariffName,
                             BigDecimal unitPrice, int quantity) {
        return addItem(tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity, null, null);
    }

    /**
     * Adds a tariff item to this order, recording which campaign (if any) discounted
     * {@code unitPrice} (Feature 21.3.3, ADR-027 Decision Section 4).
     */
    public OrderItem addItem(UUID tariffId, String tariffCode, int tariffVersion, String tariffName,
                             BigDecimal unitPrice, int quantity, UUID campaignId, String campaignCode) {
        return addTariffItem(tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity,
                campaignId, campaignCode, null);
    }

    /**
     * Adds a {@link OrderItemType#TARIFF} item. {@code targetSubscriptionId} is non-null only on a
     * {@link OrderType#PLAN_CHANGE} order's single item (Sprint 24 design-note D2).
     */
    public OrderItem addTariffItem(UUID tariffId, String tariffCode, int tariffVersion, String tariffName,
                                   BigDecimal unitPrice, int quantity, UUID campaignId,
                                   String campaignCode, UUID targetSubscriptionId) {
        OrderItem item = OrderItem.forTariff(this, tariffId, tariffCode, tariffVersion, tariffName,
                unitPrice, quantity, campaignId, campaignCode, targetSubscriptionId);
        items.add(item);
        return item;
    }

    /**
     * Adds an {@link OrderItemType#ADDON} item snapshotted from the catalog addon (Sprint 24
     * design-note D1). {@code targetSubscriptionId} is non-null for standalone {@link OrderType#ADDON}
     * orders and null for addons bundled into a {@link OrderType#NEW_LINE} order.
     */
    public OrderItem addAddonItem(String productCode, String productName, String addonType,
                                  String currency, BigDecimal unitPrice,
                                  int quantity, UUID targetSubscriptionId, Long allowanceDataMb,
                                  Long allowanceMinutes, Long allowanceSms) {
        OrderItem item = OrderItem.forAddon(this, productCode, productName, addonType, currency,
                unitPrice, quantity,
                targetSubscriptionId, allowanceDataMb, allowanceMinutes, allowanceSms);
        items.add(item);
        return item;
    }

    /**
     * Transitions this order to {@link OrderStatus#CANCELLED}.
     *
     * <p>Allowed from PENDING (customer cancellation of an unpaid order) or CONFIRMED (saga
     * compensation after a post-payment failure, e.g. {@code payment.refunded.v1}). The terminal
     * states FULFILLED and FAILED may never be cancelled; any disallowed source status triggers
     * {@link BusinessRuleException}.
     */
    public void cancel() {
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Cannot cancel order in status: " + this.status.name()
                            + ". Only PENDING or CONFIRMED orders may be cancelled.");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** Transitions this order to {@link OrderStatus#CONFIRMED} (used by saga). */
    public void confirm() {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessRuleException(
                    "Cannot confirm order in status: " + this.status.name());
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions this order to {@link OrderStatus#FULFILLED} (terminal success, used by saga on
     * {@code subscription.activated.v1}). Only a CONFIRMED order may be fulfilled; any other status
     * triggers {@link BusinessRuleException}.
     */
    public void fulfill() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Cannot fulfill order in status: " + this.status.name()
                            + ". Only CONFIRMED orders may be fulfilled.");
        }
        this.status = OrderStatus.FULFILLED;
        this.updatedAt = Instant.now();
    }

    /**
     * Transitions this order to {@link OrderStatus#FAILED} (used by saga on payment failure).
     *
     * <p>Allowed only from PENDING or CONFIRMED (an in-flight order whose saga aborts). The terminal
     * states FULFILLED, FAILED and CANCELLED may never be failed; any disallowed source status
     * triggers {@link BusinessRuleException}.
     */
    public void fail() {
        if (this.status != OrderStatus.PENDING && this.status != OrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Cannot fail order in status: " + this.status.name()
                            + ". Only PENDING or CONFIRMED orders may be failed.");
        }
        this.status = OrderStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    /** The kind of order (NEW_LINE | ADDON | PLAN_CHANGE), derived from its items at creation. */
    public OrderType getOrderType() {
        return orderType;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Unmodifiable view of order line items. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
