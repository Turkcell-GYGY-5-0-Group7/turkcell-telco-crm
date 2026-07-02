package com.telco.usage.application.query;

import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.platform.common.api.CursorPage;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Returns cursor-paginated CDR history for a subscription within a time range (ADR-015).
 *
 * @param subscriptionId the subscription to query
 * @param from           inclusive start of the time range
 * @param to             inclusive end of the time range
 * @param cursor         opaque cursor from the previous page; null for the first page
 * @param limit          page size (1-200)
 * @param principalId    JWT sub of the calling user; null means ADMIN (ownership check skipped)
 */
public record GetUsageHistoryQuery(

        @NotNull UUID subscriptionId,
        @NotNull Instant from,
        @NotNull Instant to,
        String cursor,
        @Min(1) @Max(200) int limit,
        String principalId

) implements Query<CursorPage<UsageHistoryItem>> {
}
