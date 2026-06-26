package com.telco.order.application.dto;

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
        List<OrderItemRequest> items

) {
}
