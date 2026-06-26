package com.telco.customer.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code customer.registered.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox. Aligns with the Avro contract
 * {@code com.telco.platform.events.customer.CustomerRegisteredV1}.
 *
 * <p>The national identity number is deliberately omitted (left null) for PII minimization (KVKK):
 * downstream onboarding consumers (subscription, notification) do not require it.
 */
public record CustomerRegisteredV1(
        String customerId,
        String type,
        String identityNumber,
        String status,
        long registeredAt
) implements Event {

    public static CustomerRegisteredV1 of(String customerId, String type, String status,
                                          long registeredAt) {
        return new CustomerRegisteredV1(customerId, type, null, status, registeredAt);
    }
}
