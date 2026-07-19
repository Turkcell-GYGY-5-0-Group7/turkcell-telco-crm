package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Creates a {@code RESERVED} {@code CampaignRedemption} row for one campaign-priced
 * {@code order.created.v1} line item, keyed by {@code (campaignId, customerId, orderId)}
 * (Feature 21.4.3, ADR-027 Decision Section 4). This is what makes the per-customer/total
 * redemption-cap counting rule race-safe: the slot is held from the moment the order exists, not only
 * once payment clears.
 *
 * <p>Idempotency key is {@code messageId + ":" + campaignId}, not {@code messageId} alone: a single
 * {@code order.created.v1} message can carry multiple items priced against different campaigns, each
 * needing its own dedup identity within the same Kafka record (see
 * {@link com.telco.campaign.application.consumer.OrderCreatedRedemptionReservationConsumer}).
 */
public record CreateRedemptionReservationCommand(

        @NotNull
        UUID campaignId,

        @NotNull
        UUID customerId,

        @NotNull
        UUID orderId,

        /** Kafka messageId (record key) plus the campaignId, disambiguating multi-item messages. */
        @NotNull
        String idempotencyKey

) implements Command<Void>, IdempotentRequest {
}
