package com.telco.usage.application.query;

import com.telco.usage.application.dto.QuotaResponse;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Returns the currently active quota for a subscription.
 *
 * @param subscriptionId the subscription to query
 * @param principalId    JWT sub of the calling user; null means ADMIN (ownership check skipped)
 */
public record GetQuotaQuery(

        @NotNull
        UUID subscriptionId,

        String principalId

) implements Query<QuotaResponse> {
}
