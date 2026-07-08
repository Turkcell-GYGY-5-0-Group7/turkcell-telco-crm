package com.telco.usage.api;

import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.application.dto.QuotaResponse;
import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.usage.application.query.GetQuotaQuery;
import com.telco.usage.application.query.GetUsageHistoryQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.CursorPage;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Usage API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;
    private final CurrentUserProvider currentUserProvider;

    public UsageController(Mediator mediator, ApiResponseFactory responses,
                           CurrentUserProvider currentUserProvider) {
        this.mediator = mediator;
        this.responses = responses;
        this.currentUserProvider = currentUserProvider;
    }

    /** Returns the active quota for a subscription. */
    @GetMapping("/subscriptions/{subscriptionId}/quota")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBSCRIBER')")
    public ApiResult<QuotaResponse> getQuota(
            @PathVariable UUID subscriptionId,
            Authentication authentication) {
        return responses.ok(mediator.query(new GetQuotaQuery(
                subscriptionId, currentUserProvider.currentUser().customerId(), isAdmin(authentication))));
    }

    /** Returns cursor-paginated CDR history for a subscription within a time range (ADR-015). */
    @GetMapping("/subscriptions/{subscriptionId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBSCRIBER')")
    public ApiResult<CursorPage<UsageHistoryItem>> getHistory(
            @PathVariable UUID subscriptionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        return responses.ok(mediator.query(new GetUsageHistoryQuery(
                subscriptionId, from, to, cursor, limit,
                currentUserProvider.currentUser().customerId(), isAdmin(authentication))));
    }

    /** Triggers period aggregation for a subscription. ADMIN only. */
    @PostMapping("/subscriptions/{subscriptionId}/aggregate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UsageAggregateResponse> aggregate(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody AggregateRequest request) {
        AggregateUsageCommand command = new AggregateUsageCommand(
                subscriptionId, request.periodStart(), request.periodEnd());
        return responses.ok(mediator.send(command));
    }

    /** Request body for the aggregate endpoint. */
    public record AggregateRequest(
            @NotNull Instant periodStart,
            @NotNull Instant periodEnd
    ) {
    }

    /** Staff bypass: true only for ROLE_ADMIN, unchanged from the prior behavior. */
    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
