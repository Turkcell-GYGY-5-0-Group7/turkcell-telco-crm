package com.telco.subscription.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.subscription.application.command.ActivateSubscriptionCommand;
import com.telco.subscription.application.dto.ActivateSubscriptionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal (saga-driven) subscription activation endpoint. The gateway blocks {@code /internal/**}
 * from external traffic (ADR-011), so only intra-cluster services can reach this endpoint. No
 * ownership guard or role check is needed: the saga's correlation guarantees the caller is trusted.
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
}
