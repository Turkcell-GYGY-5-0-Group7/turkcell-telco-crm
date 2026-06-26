package com.telco.order.application.command;

import com.telco.order.application.dto.OrderResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Cancels a PENDING order. Throws BusinessRuleException if the order is in any other status. */
public record CancelOrderCommand(

        @NotNull
        UUID orderId,

        String reason,

        /** Keycloak subject (JWT sub) of the authenticated caller. */
        String callerUserId,

        boolean callerIsAdmin

) implements Command<OrderResponse> {
}
