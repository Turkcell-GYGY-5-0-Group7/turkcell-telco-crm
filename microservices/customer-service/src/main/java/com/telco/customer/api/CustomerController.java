package com.telco.customer.api;

import com.telco.customer.application.command.DeleteCustomerCommand;
import com.telco.customer.application.command.RegisterCustomerCommand;
import com.telco.customer.application.command.UpdateCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.dto.RegisterCustomerRequest;
import com.telco.customer.application.dto.UpdateCustomerRequest;
import com.telco.customer.application.query.GetCustomerQuery;
import com.telco.customer.application.query.ListCustomersQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Customer management API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here. All endpoints require a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUBSCRIBER', 'CALL_CENTER_AGENT', 'DEALER', 'ADMIN')")
    public ApiResult<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return responses.ok(mediator.send(new RegisterCustomerCommand(
                request.type(), request.firstName(), request.lastName(),
                request.identityNumber(), request.dateOfBirth())));
    }

    // Staff-only until the customerId-to-Keycloak-subject linkage lands (see
    // docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md); a
    // SUBSCRIBER caller cannot yet be verified as the owner of a given customer record.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<CustomerResponse> get(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetCustomerQuery(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<PageResult<CustomerResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new ListCustomersQuery(page, size)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<CustomerResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateCustomerRequest request) {
        return responses.ok(mediator.send(new UpdateCustomerCommand(
                id, request.firstName(), request.lastName(), request.dateOfBirth())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Unit> delete(@PathVariable UUID id) {
        return responses.ok(mediator.send(new DeleteCustomerCommand(id)));
    }
}
