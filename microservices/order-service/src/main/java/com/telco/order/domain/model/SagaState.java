package com.telco.order.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks the current step and status of a saga orchestration for an order (FR-11).
 * {@code payload} is stored as JSONB to allow flexible per-step metadata.
 */
@Entity
@Table(name = "saga_state")
public class SagaState {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false, length = 64)
    private String step;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected SagaState() {
    }

    private SagaState(UUID id, UUID orderId, String step, String status, String payload) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.step = Objects.requireNonNull(step, "step");
        this.status = Objects.requireNonNull(status, "status");
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    /** Creates the initial saga state record when an order is placed. */
    public static SagaState init(UUID orderId, String step, String status, String payload) {
        return new SagaState(UUID.randomUUID(), orderId, step, status, payload);
    }

    public void advance(String newStep, String newStatus, String newPayload) {
        this.step = Objects.requireNonNull(newStep, "step");
        this.status = Objects.requireNonNull(newStatus, "status");
        this.payload = newPayload;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getStep() {
        return step;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
