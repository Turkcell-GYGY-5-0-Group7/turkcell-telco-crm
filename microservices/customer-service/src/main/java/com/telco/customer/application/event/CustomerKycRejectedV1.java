package com.telco.customer.application.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code customer.kyc-rejected.v1} (ADR-009,
 * ADR-019). Aligns with the Avro contract
 * {@code com.telco.platform.events.customer.CustomerKycRejectedV1}.
 */
public record CustomerKycRejectedV1(
        String customerId,
        String status,
        String reason,
        long rejectedAt
) implements Event {
}
