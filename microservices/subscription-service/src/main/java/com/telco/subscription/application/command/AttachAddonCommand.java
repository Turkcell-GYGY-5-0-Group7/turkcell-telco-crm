package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Applies an ADDON order to an existing subscription (FR-09), recording the addon and emitting
 * {@code subscription.addon-attached.v1} so billing can invoice the fee (FR-22). Dispatched by the
 * {@code order.created.v1} consumer with the addon price/type snapshot the order event carries.
 *
 * <p>Idempotency: an {@link IdempotentRequest} keyed on the Kafka messageId, plus the
 * {@code (order_id, addon_code)} unique index as a second line of defense.
 */
public record AttachAddonCommand(
        @NotNull
        UUID subscriptionId,

        @NotNull
        UUID orderId,

        /** Customer the order was placed for; must own the target subscription (guarded). */
        @NotNull
        UUID customerId,

        @NotBlank
        String addonCode,

        String addonType,

        @NotNull
        BigDecimal price,

        @NotBlank
        String currency,

        @NotBlank
        String messageId
) implements Command<UUID>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
