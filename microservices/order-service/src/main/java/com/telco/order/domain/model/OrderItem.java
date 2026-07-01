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

    /** For JPA only. */
    protected OrderItem() {
    }

    private OrderItem(UUID id, Order order, UUID tariffId, String tariffCode, int tariffVersion,
                      String tariffName, BigDecimal unitPrice, int quantity) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = Objects.requireNonNull(order, "order");
        this.tariffId = Objects.requireNonNull(tariffId, "tariffId");
        this.tariffCode = Objects.requireNonNull(tariffCode, "tariffCode");
        this.tariffVersion = tariffVersion;
        this.tariffName = tariffName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(Order order, UUID tariffId, String tariffCode, int tariffVersion,
                                   String tariffName, BigDecimal unitPrice, int quantity) {
        return new OrderItem(UUID.randomUUID(), order, tariffId, tariffCode, tariffVersion,
                tariffName, unitPrice, quantity);
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
}
