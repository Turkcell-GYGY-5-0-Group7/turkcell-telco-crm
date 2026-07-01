package com.telco.billing.api;

import com.telco.billing.api.dto.BillRunRequest;
import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.platform.mediator.Mediator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
class BillingController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    BillingController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    ApiResult<RunBillResult> triggerBillRun(@Valid @RequestBody BillRunRequest request) {
        RunBillResult result = mediator.send(new RunBillCommand(request.periodStart(), request.periodEnd()));
        return responses.ok(result);
    }
}
