package com.telco.order.application.command;

import com.telco.order.application.dto.OrderItemRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.domain.model.OrderType;
import com.telco.platform.cqrs.Command;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Places a new order for a customer. Idempotent: identical idempotency keys return the existing order. */
public record CreateOrderCommand(

        @NotNull
        UUID customerId,

        @NotBlank @Size(max = 64)
        String idempotencyKey,

        @NotEmpty @Valid
        List<OrderItemRequest> items,

        /** Keycloak subject (JWT sub) of the authenticated caller — set by the controller, never from the request body. */
        @NotBlank
        String userId,

        /** NEW_LINE when null (pre-FR-09 callers), else PLAN_CHANGE or ADDON. */
        OrderType orderType,

        /** Target subscription; required for PLAN_CHANGE/ADDON (handler-validated), null on NEW_LINE. */
        UUID subscriptionId

) implements Command<OrderResponse> {

    /** Legacy NEW_LINE shape - keeps pre-FR-09 callers and tests unchanged. */
    public CreateOrderCommand(UUID customerId, String idempotencyKey,
                              List<OrderItemRequest> items, String userId) {
        this(customerId, idempotencyKey, items, userId, null, null);
    }
}
