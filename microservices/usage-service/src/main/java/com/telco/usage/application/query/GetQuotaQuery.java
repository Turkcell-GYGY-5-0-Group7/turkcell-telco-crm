package com.telco.usage.application.query;

import com.telco.usage.application.dto.QuotaResponse;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Returns the currently active quota for a subscription.
 *
 * @param subscriptionId   the subscription to query
 * @param callerCustomerId resolved {@code customerId} claim linked to the caller's identity
 *                         (identity-to-customer linkage, ADR-011); null when the caller is staff
 *                         or the identity is not yet linked. Never treated as a bypass on its own -
 *                         see {@code callerIsAdmin} for the staff bypass.
 * @param callerIsAdmin    staff bypass; ownership check is skipped only when this is true
 */
public record GetQuotaQuery(

        @NotNull
        UUID subscriptionId,

        String callerCustomerId,

        boolean callerIsAdmin

) implements Query<QuotaResponse> {
}
