package com.telco.subscription.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.command.ReactivateSubscriptionCommand;
import com.telco.subscription.application.command.SuspendSubscriptionCommand;
import com.telco.subscription.application.command.TerminateSubscriptionCommand;
import com.telco.subscription.application.dto.ActivateSubscriptionRequest;
import com.telco.subscription.application.dto.SubscriptionResponse;
import com.telco.subscription.application.dto.SuspendSubscriptionRequest;
import com.telco.subscription.application.query.GetSubscriptionQuery;
import com.telco.subscription.application.query.GetSubscriptionsByCustomerQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Subscription API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-008, ADR-015). No business logic here; the state machine and event emission live in
 * the aggregate and the command handlers.
 *
 * <p>Activation ({@code POST /api/v1/subscriptions}) is internal/saga-driven (per contract): it
 * requires a valid JWT but no specific role, since the saga (9.4) calls it service-to-service. The
 * lifecycle endpoints (suspend/reactivate/terminate) require CUSTOMER or ADMIN; reads require a valid
 * JWT.
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

    /** Internal (saga-driven): allocate an MSISDN and activate a subscription. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<UUID> activate(@Valid @RequestBody ActivateSubscriptionRequest request) {
        UUID subscriptionId = mediator.send(new ActivateSubscriptionCommand(
                request.orderId(),
                request.customerId(),
                request.tariffCode(),
                request.tariffVersion()));
        return responses.ok(subscriptionId);
    }

    @GetMapping("/{id}")
    public ApiResult<SubscriptionResponse> get(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetSubscriptionQuery(id)));
    }

    @GetMapping
    public ApiResult<PageResult<SubscriptionResponse>> getByCustomer(
            @RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new GetSubscriptionsByCustomerQuery(customerId, page, size)));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> suspend(
            @PathVariable UUID id,
            @RequestBody(required = false) SuspendSubscriptionRequest request) {
        String reason = request != null ? request.reason() : null;
        return responses.ok(mediator.send(new SuspendSubscriptionCommand(id, reason)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> reactivate(@PathVariable UUID id) {
        return responses.ok(mediator.send(new ReactivateSubscriptionCommand(id)));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> terminate(@PathVariable UUID id) {
        return responses.ok(mediator.send(new TerminateSubscriptionCommand(id)));
    }
}
