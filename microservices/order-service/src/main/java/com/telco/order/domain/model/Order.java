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

    private Order(UUID id, UUID customerId, String idempotencyKey, BigDecimal totalAmount, String userId) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Creates a new order in {@link OrderStatus#PENDING} state. */
    public static Order create(UUID customerId, String idempotencyKey, BigDecimal totalAmount, String userId) {
        return new Order(UUID.randomUUID(), customerId, idempotencyKey, totalAmount, userId);
    }

    /**
     * Adds an item to this order. Callers use this to build the item list before persisting.
     */
    public OrderItem addItem(UUID tariffId, String tariffCode, int tariffVersion, String tariffName,
                             BigDecimal unitPrice, int quantity) {
        OrderItem item = OrderItem.create(this, tariffId, tariffCode, tariffVersion, tariffName, unitPrice, quantity);
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
