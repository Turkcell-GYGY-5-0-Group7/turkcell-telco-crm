package com.telco.customer.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code customer.kyc-approved.v1} (ADR-009,
 * ADR-019). Aligns with the Avro contract
 * {@code com.telco.platform.events.customer.CustomerKycApprovedV1}.
 */
public record CustomerKycApprovedV1(
        String customerId,
        String status,
        long approvedAt
) implements Event {
}
