package com.telco.customer.api;

import com.telco.customer.application.command.ApproveKycCommand;
import com.telco.customer.application.command.RejectKycCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.dto.RejectKycRequest;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * KYC decision API (FR-02, AC-01 step 3). Restricted to back-office operators: approval/rejection
 * requires the ADMIN role (RBAC, ADR-011).
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/kyc")
public class CustomerKycController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerKycController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CustomerResponse> approve(@PathVariable UUID customerId) {
        return responses.ok(mediator.send(new ApproveKycCommand(customerId)));
    }

    @PostMapping("/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<CustomerResponse> reject(@PathVariable UUID customerId,
                                              @RequestBody(required = false) RejectKycRequest request) {
        String reason = request != null ? request.reason() : null;
        return responses.ok(mediator.send(new RejectKycCommand(customerId, reason)));
    }
}
