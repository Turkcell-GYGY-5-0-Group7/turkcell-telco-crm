package com.telco.usage.api;

import com.telco.usage.application.command.AggregateUsageCommand;
import com.telco.usage.application.dto.QuotaResponse;
import com.telco.usage.application.dto.UsageAggregateResponse;
import com.telco.usage.application.dto.UsageHistoryItem;
import com.telco.usage.application.query.GetQuotaQuery;
import com.telco.usage.application.query.GetUsageHistoryQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    public UsageController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /** Returns the active quota for a subscription. */
    @GetMapping("/subscriptions/{subscriptionId}/quota")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ApiResult<QuotaResponse> getQuota(
            @PathVariable UUID subscriptionId,
            Authentication authentication) {
        return responses.ok(mediator.query(
                new GetQuotaQuery(subscriptionId, principalId(authentication))));
    }

    /** Returns paginated CDR history for a subscription within a time range. */
    @GetMapping("/subscriptions/{subscriptionId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'CUSTOMER')")
    public ApiResult<Page<UsageHistoryItem>> getHistory(
            @PathVariable UUID subscriptionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return responses.ok(mediator.query(
                new GetUsageHistoryQuery(subscriptionId, from, to, pageable, principalId(authentication))));
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

    /**
     * Returns the JWT sub (= X-User-Id forwarded by gateway) for CUSTOMER callers,
     * or null for ADMIN callers (ownership check bypassed).
     */
    private String principalId(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? null : authentication.getName();
    }
}
