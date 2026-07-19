package com.telco.order.application.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Versioned event payload published to the outbox as {@code order.created.v1} (ADR-009, ADR-019).
 * Serialized to JSON by starter-outbox; an {@code eventId} is injected automatically for
 * consumer idempotency. Payload matches the Avro schema contract in event-catalog.md.
 */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        List<OrderItemPayload> items,
        BigDecimal totalAmount,
        String idempotencyKey,
        String occurredAt
) implements Event {

    /**
     * Lightweight item snapshot embedded in the event. {@code campaignId} is a nullable, additive
     * field (Feature 21.3.3, ADR-027 Decision Section 4 third ratification addendum, Avro-backward-
     * compatible per ADR-019): the campaign, if any, that discounted {@code unitPrice}. This is what
     * lets campaign-service's Feature 21.4 {@code order.created.v1} consumer create the correctly
     * attributed {@code RESERVED} redemption row.
     *
     * <p>{@code itemType} (defaulting to {@code "TARIFF"} in the Avro contract), {@code productCode}
     * and {@code targetSubscriptionId} are the Sprint 24 Feature 24.2 additive fields (design-note
     * D1/D2). For ADDON items the contract-mandatory non-null {@code tariffId}/{@code tariffName}
     * fields are generalized to the catalog product snapshot: {@code tariffId} carries the addon's
     * catalog id and {@code tariffName} the addon name (the field names predate item-type
     * generalization; keeping them non-null preserves Avro backward compatibility).
     */
    public record OrderItemPayload(
            String tariffId,
            String tariffName,
            BigDecimal unitPrice,
            int quantity,
            String campaignId,
            String itemType,
            String productCode,
            String targetSubscriptionId
    ) {
    }
}
