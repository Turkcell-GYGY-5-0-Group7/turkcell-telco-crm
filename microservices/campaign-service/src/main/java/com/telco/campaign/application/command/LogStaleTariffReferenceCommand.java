package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.inbox.IdempotentRequest;
import jakarta.validation.constraints.NotBlank;

/**
 * Logs a WARN diagnostic when a newly (re)created tariff code is referenced by an ACTIVE campaign's
 * {@code applicableTariffCodes} (Feature 21.4.3, {@code tariff.created.v1} consumer). Deliberately
 * read-only - does not set {@link com.telco.campaign.domain.model.Campaign#flagStaleTariffReference}
 * (that flag is reserved for the {@code tariff.price-changed.v1} path, which is the stronger,
 * unambiguous defensive signal - a brand-new tariff being created is normal catalog churn, not
 * necessarily a problem, so this consumer surfaces it as a lighter, log-only diagnostic rather than an
 * admin-visible flag; see {@code docs/api-contracts/campaign-service.md} "Tariff-defensive behavior").
 * A tariff code referenced by no ACTIVE campaign is a silent no-op.
 */
public record LogStaleTariffReferenceCommand(

        @NotBlank
        String tariffCode,

        /** Kafka messageId (record key) - the stable inbox dedup key. */
        @NotBlank
        String idempotencyKey

) implements Command<Void>, IdempotentRequest {
}
