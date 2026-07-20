package com.telco.order.application.dto;

import com.telco.order.domain.model.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** HTTP request body for POST /api/v1/orders. The Idempotency-Key comes from the header. */
public record CreateOrderRequest(

        @NotNull
        UUID customerId,

        @NotEmpty @Valid
        List<OrderItemRequest> items,

        /** NEW_LINE (default when omitted), PLAN_CHANGE, or ADDON (FR-09). */
        OrderType orderType,

        /** Target subscription; required for PLAN_CHANGE/ADDON, null on NEW_LINE (FR-09). */
        UUID subscriptionId

) {

    /** Backward-compatible overload: a plain NEW_LINE order. */
    public CreateOrderRequest(UUID customerId, List<OrderItemRequest> items) {
        this(customerId, items, null, null);
    }
}
