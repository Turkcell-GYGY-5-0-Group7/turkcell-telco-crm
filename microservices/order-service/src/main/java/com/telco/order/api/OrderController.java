package com.telco.order.api;

import com.telco.order.application.command.CancelOrderCommand;
import com.telco.order.application.command.CreateOrderCommand;
import com.telco.order.application.dto.CreateOrderRequest;
import com.telco.order.application.dto.OrderResponse;
import com.telco.order.application.query.GetOrderQuery;
import com.telco.order.application.query.GetOrdersByCustomerQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Order management API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult} (ADR-004, ADR-015). */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public OrderController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<OrderResponse> createOrder(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        CreateOrderCommand command = new CreateOrderCommand(
                request.customerId(),
                idempotencyKey,
                request.items(),
                authentication.getName()
        );
        return responses.ok(mediator.send(command));
    }

    @GetMapping("/{orderId}")
    public ApiResult<OrderResponse> getOrder(Authentication authentication, @PathVariable UUID orderId) {
        return responses.ok(mediator.query(
                new GetOrderQuery(orderId, authentication.getName(), isAdmin(authentication))));
    }

    @GetMapping("/customer/{customerId}")
    public ApiResult<PageResult<OrderResponse>> getOrdersByCustomer(
            Authentication authentication,
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(
                new GetOrdersByCustomerQuery(customerId, page, size, authentication.getName(), isAdmin(authentication))));
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('SUBSCRIBER') or hasRole('ADMIN')")
    public ApiResult<OrderResponse> cancelOrder(
            Authentication authentication,
            @PathVariable UUID orderId,
            @RequestParam(required = false) String reason) {
        return responses.ok(mediator.send(
                new CancelOrderCommand(orderId, reason, authentication.getName(), isAdmin(authentication))));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
