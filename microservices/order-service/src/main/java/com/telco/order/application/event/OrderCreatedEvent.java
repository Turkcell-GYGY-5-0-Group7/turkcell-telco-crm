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
        String occurredAt,
        /** NEW_LINE, PLAN_CHANGE, or ADDON (FR-09); nullable-additive, null means NEW_LINE. */
        String orderType,
        /** Target subscription for PLAN_CHANGE/ADDON orders; null on NEW_LINE. */
        String subscriptionId
) implements Event {

    /**
     * Lightweight item snapshot embedded in the event. {@code campaignId} is a nullable, additive
     * field (Feature 21.3.3, ADR-027 Decision Section 4 third ratification addendum, Avro-backward-
     * compatible per ADR-019): the campaign, if any, that discounted {@code unitPrice}. This is what
     * lets campaign-service's Feature 21.4 {@code order.created.v1} consumer create the correctly
     * attributed {@code RESERVED} redemption row.
     */
    public record OrderItemPayload(
            String tariffId,
            String tariffName,
            BigDecimal unitPrice,
            int quantity,
            String campaignId,
            /** Catalog addon code for ADDON order items (FR-09); null on tariff items. */
            String addonCode,
            /** Addon type snapshot (DATA/SMS/MINUTES/VAS) for ADDON items; null on tariff items. */
            String addonType,
            /** Tariff code snapshot (FR-09) so PLAN_CHANGE needs no sync order lookup; null on addon items. */
            String tariffCode,
            /** ISO 4217 currency of unitPrice (FR-09). */
            String currency
    ) {
    }
}
