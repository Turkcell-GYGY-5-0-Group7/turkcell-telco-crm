package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Releases the {@code CampaignRedemption} for {@code orderId} (RESERVED -&gt; RELEASED), driven by
 * consuming {@code order.cancelled.v1} (Feature 21.4.2). A no-op (not an error) if no redemption row
 * exists for {@code orderId}, or if it is not currently RESERVED (already CONFIRMED/RELEASED -
 * redelivery-safe).
 *
 * <p>Saga-only command: implements {@link IdempotentRequest} with {@link #idempotencyKey()} carrying
 * the Kafka {@code messageId} (the record key) so the platform {@code InboxBehavior} dedups
 * redelivery atomically INSIDE the handler transaction.
 */
public record ReleaseRedemptionCommand(

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
