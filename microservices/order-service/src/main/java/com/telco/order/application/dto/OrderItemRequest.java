package com.telco.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request DTO for a single line item within a create-order request. */
public record OrderItemRequest(

        @NotNull
        UUID tariffId,

        @Min(1)
        int quantity

) {
}
