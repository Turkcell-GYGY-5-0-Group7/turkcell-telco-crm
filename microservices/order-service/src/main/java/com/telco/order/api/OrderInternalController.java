package com.telco.order.api;

import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrderInternalQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal, system-to-system order read for the onboarding saga's synchronous hop. The
 * subscription-service calls {@code GET /internal/orders/{orderId}} during activation to read the
 * order's items (tariffCode/tariffVersion) and customerId.
 *
 * <p>Trusted endpoint (tech-lead ruling 1a): NO JWT requirement and NO customer-ownership guard -
 * it is permitted in {@link OrderSecurityConfig} and the gateway excludes {@code /internal/**} from
 * public traffic (handled by devops). It dispatches the distinct {@link GetOrderInternalQuery},
 * never the guarded {@link com.telco.order.application.query.GetOrderQuery}.
 */
@RestController
@RequestMapping("/internal/orders")
public class OrderInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public OrderInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping("/{orderId}")
    public ApiResult<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return responses.ok(mediator.query(new GetOrderInternalQuery(orderId)));
    }
}
