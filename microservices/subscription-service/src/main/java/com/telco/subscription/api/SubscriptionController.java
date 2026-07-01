package com.telco.subscription.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.subscription.application.command.ReactivateSubscriptionCommand;
import com.telco.subscription.application.command.SuspendSubscriptionCommand;
import com.telco.subscription.application.command.TerminateSubscriptionCommand;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.dto.SuspendSubscriptionRequest;
import com.telco.subscription.application.query.GetSubscriptionQuery;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Subscription API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-008, ADR-015). No business logic here; the state machine and event emission live in
 * the aggregate and the command handlers.
 *
 * <p>All lifecycle and read endpoints require CUSTOMER or ADMIN. Ownership is enforced in the
 * command/query handlers: a CUSTOMER caller may only operate on their own subscriptions. Saga-driven
 * activation is served by {@link SubscriptionInternalController} under {@code /internal/subscriptions},
 * which the gateway blocks from external traffic (ADR-011).
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public SubscriptionController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<SubscriptionResponse> get(
            Authentication authentication,
            @PathVariable UUID id) {
        return responses.ok(mediator.query(
                new GetSubscriptionQuery(id, authentication.getName(), isAdmin(authentication))));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<PageResult<SubscriptionResponse>> getByCustomer(
            Authentication authentication,
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(
                new GetSubscriptionsByCustomerQuery(
                        customerId, page, size, authentication.getName(), isAdmin(authentication))));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> suspend(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid SuspendSubscriptionRequest request) {
        String reason = request != null ? request.reason() : null;
        return responses.ok(mediator.send(
                new SuspendSubscriptionCommand(id, reason, authentication.getName(), isAdmin(authentication))));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> reactivate(
            Authentication authentication,
            @PathVariable UUID id) {
        return responses.ok(mediator.send(
                new ReactivateSubscriptionCommand(id, authentication.getName(), isAdmin(authentication))));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> terminate(
            Authentication authentication,
            @PathVariable UUID id) {
        return responses.ok(mediator.send(
                new TerminateSubscriptionCommand(id, authentication.getName(), isAdmin(authentication))));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
