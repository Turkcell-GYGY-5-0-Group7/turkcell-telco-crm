package com.telco.usage.application.query;

import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.platform.cqrs.Query;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/**
 * Returns paginated CDR history for a subscription within a time range.
 *
 * @param subscriptionId the subscription to query
 * @param from           inclusive start of the time range
 * @param to             exclusive end of the time range
 * @param pageable       pagination parameters
 * @param principalId    JWT sub of the calling user; null means ADMIN (ownership check skipped)
 */
public record GetUsageHistoryQuery(

        @NotNull
        UUID subscriptionId,

        @NotNull
        Instant from,

        @NotNull
        Instant to,

        @NotNull
        Pageable pageable,

        String principalId

) implements Query<Page<UsageHistoryItem>> {
}
