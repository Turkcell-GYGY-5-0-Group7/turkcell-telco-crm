package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Confirms the {@code CampaignRedemption} for {@code orderId} (RESERVED -&gt; CONFIRMED), driven by
 * consuming {@code payment.completed.v1} - the "order is real" trigger per ADR-027 Section 4
 * ratification (Feature 21.4.2). A no-op (not an error) if no redemption row exists for
 * {@code orderId} (the order had no campaign applied).
 *
 * <p>Saga-only command: implements {@link IdempotentRequest} with {@link #idempotencyKey()} carrying
 * the Kafka {@code messageId} (the record key) so the platform {@code InboxBehavior} dedups
 * redelivery atomically INSIDE the handler transaction, mirroring order-service's
 * {@code ConfirmOrderCommand}.
 */
public record ConfirmRedemptionCommand(

        @NotNull
        UUID orderId,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotNull
        String messageId

) implements Command<Void>, IdempotentRequest {

    @Override
    public String idempotencyKey() {
        return messageId;
    }
}
