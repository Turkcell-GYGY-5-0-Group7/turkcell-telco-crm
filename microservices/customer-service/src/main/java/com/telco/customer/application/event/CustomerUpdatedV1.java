package com.telco.customer.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code customer.updated.v1} (ADR-009, ADR-019).
 * Aligns with the Avro contract {@code com.telco.platform.events.customer.CustomerUpdatedV1}.
 */
public record CustomerUpdatedV1(
        String customerId,
        String firstName,
        String lastName,
        long updatedAt,
        String email,
        String phone
) implements Event {
}
