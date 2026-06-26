package com.telco.identity.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code user.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for consumer
 * idempotency.
 */
public record UserCreatedV1(
        String userId,
        String username,
        String email,
        String createdAt
) implements Event {
}
