package com.telco.dispute.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit record persisted to audit_log (ADR-028 Section 2, ADR-021, NFR-12). */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "entity", nullable = false, length = 128)
    private String entity;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID id, UUID actorId, String action, String entity, String entityId,
                    String details, String correlationId, Instant createdAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.details = details;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }
}
