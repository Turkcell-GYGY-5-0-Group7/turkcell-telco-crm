package com.telco.reference.application;

import java.time.Instant;

/**
 * Versioned event payload published to the outbox as {@code demoitem.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for consumer
 * idempotency.
 */
public record DemoItemCreatedV1(String id, String name, Instant createdAt) {
}
