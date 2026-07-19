package com.telco.subscription.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.dto.ActivateSubscriptionRequest;
import com.telco.subscription.application.dto.SubscriptionInternalResponse;
import com.telco.subscription.application.query.GetSubscriptionInternalQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal (system-to-system) subscription endpoints. The gateway blocks {@code /internal/**}
 * from external traffic (ADR-011), so only intra-cluster services can reach these endpoints. No
 * ownership guard or role check is needed: the saga's correlation (activation) and the network
 * perimeter (reads) guarantee the caller is trusted.
 *
 * <p>The subscription-service's {@link SubscriptionSecurityConfig} permits {@code /internal/**}
 * without a JWT (mirroring order-service's {@code OrderSecurityConfig}).
 */
@RestController
@RequestMapping("/internal/subscriptions")
public class SubscriptionInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public SubscriptionInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

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

    /**
     * Tokenless internal read used by order-service to validate the target subscription of ADDON
     * and PLAN_CHANGE orders (exists, ACTIVE, owned, current tariff - Sprint 24 Feature 24.2). It
     * dispatches the distinct {@link GetSubscriptionInternalQuery}, never the ownership-guarded
     * {@link com.telco.subscription.application.query.GetSubscriptionQuery}.
     */
    @GetMapping("/{subscriptionId}")
    public ApiResult<SubscriptionInternalResponse> getSubscription(@PathVariable UUID subscriptionId) {
        return responses.ok(mediator.query(new GetSubscriptionInternalQuery(subscriptionId)));
    }
}
