package com.telco.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Demo aggregate persisted via JPA. Kept deliberately small; richer domains would separate the
 * pure domain model from the persistence model (ADR-004).
 */
@Entity
@Table(name = "demo_item")
public class DemoItem {

    @Id
    private UUID id;

    @Column(nullable = false, length = 280)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemoItem() {
        // for JPA
    }

    public DemoItem(UUID id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
