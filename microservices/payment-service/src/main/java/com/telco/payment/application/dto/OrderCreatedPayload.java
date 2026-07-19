package com.telco.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for the {@code order.created.v1} event consumed from Kafka.
 * Fields match the order-service outbox event payload. Unknown fields are ignored
 * for forward-compatibility (ADR-019).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedPayload(
        String orderId,
        String customerId,
        BigDecimal totalAmount,
        String occurredAt,
        /**
         * NEW_LINE, PLAN_CHANGE, or ADDON (FR-09); null from pre-FR-09 producers means NEW_LINE.
         * Only NEW_LINE orders are charged - PLAN_CHANGE/ADDON bill on the next invoice (FR-22).
         */
        String orderType
) {
}
