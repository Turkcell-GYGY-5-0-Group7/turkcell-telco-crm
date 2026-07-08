package com.telco.customer.api;

import com.telco.customer.application.dto.CustomerInternalResponse;
import com.telco.customer.application.query.GetCustomerQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal, system-to-system customer read for other services' synchronous hops (e.g.
 * order-service validating a customer during order creation). Returns only {@code {id, status}} -
 * no PII, no masked identity number, no name.
 *
 * <p>Trusted endpoint (tech-lead ruling 14.1.1): NO JWT requirement - it is permitted in
 * {@link CustomerSecurityConfig} and the gateway excludes {@code /internal/**} from public traffic
 * (handled by devops). It reuses the same {@link GetCustomerQuery} that backs
 * {@code GET /api/v1/customers/{id}} and simply narrows the response shape; the guarded route's
 * RBAC is untouched.
 */
@RestController
@RequestMapping("/internal/customers")
public class CustomerInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping("/{id}")
    public ApiResult<CustomerInternalResponse> getCustomer(@PathVariable UUID id) {
        return responses.ok(CustomerInternalResponse.from(mediator.query(new GetCustomerQuery(id))));
    }
}
