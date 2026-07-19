package com.telco.order.application.event;

import com.telco.platform.cqrs.Event;

import java.math.BigDecimal;

/**
 * Versioned event payload published to the outbox as {@code addon.purchased.v1} (Sprint 24 Feature
 * 24.3, design-note D1/D3; ADR-009, ADR-019). One event is published per ADDON order item when the
 * owning order is fulfilled, with outbox aggregate_type {@code addon} (routed to the
 * {@code addon.events} topic by the Debezium EventRouter) and aggregate_id = the order-item id, so
 * each item is an independently deduplicable message. Serialized to JSON by starter-outbox; payload
 * matches the canonical {@code addon-purchased.avsc} contract.
 *
 * <p>All catalog values are the immutable snapshot taken at order-creation time (V5
 * tariff-snapshot precedent): no runtime catalog coupling at publish time or for consumers.
 * {@code price} is the per-unit price; allowance deltas are per unit - consumers multiply by
 * {@code quantity}. {@code addonType}/{@code currency} are null only for ADDON items created
 * before the V9 snapshot columns existed; consumers fall back to TRY for a missing currency.
 */
public record AddonPurchasedEvent(
        String orderId,
        String customerId,
        String subscriptionId,
        String addonCode,
        String addonName,
        String addonType,
        BigDecimal price,
        String currency,
        int quantity,
        Long allowanceDataMb,
        Long allowanceMinutes,
        Long allowanceSms,
        String occurredAt
) implements Event {
}
