package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;

/**
 * Defensively flags every ACTIVE campaign whose {@code applicableTariffCodes} references
 * {@code tariffCode}, driven by consuming {@code tariff.price-changed.v1} (Feature 21.4.3, ADR-027
 * Decision Section 4). campaign-service never mirrors tariff pricing data - this only sets an
 * admin-visible flag/reason on the affected {@code Campaign} rows (chosen behavior; see
 * {@code docs/api-contracts/campaign-service.md}), it does not auto-{@code expire()} them. A tariff
 * code referenced by no ACTIVE campaign is a silent no-op.
 */
public record FlagStaleTariffReferenceCommand(

        @NotBlank
        String tariffCode,

        @NotBlank
        String reason,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotBlank
        String idempotencyKey

) implements Command<Void>, IdempotentRequest {
}
