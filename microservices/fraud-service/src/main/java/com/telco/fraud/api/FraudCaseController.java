package com.telco.fraud.api;

import com.telco.fraud.application.command.ResolveFraudCaseCommand;
import com.telco.fraud.application.dto.FraudCaseDetailResponse;
import com.telco.fraud.application.dto.FraudCaseSummaryResponse;
import com.telco.fraud.application.dto.ResolveFraudCaseRequest;
import com.telco.fraud.application.query.GetFraudCaseQuery;
import com.telco.fraud.application.query.GetFraudCasesQuery;
import com.telco.fraud.domain.FraudCaseStatus;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Fraud-case investigation and resolution surface (Feature 23.3.1 / 23.3.2). Thin edge: HTTP ->
 * query/command via {@link Mediator} -> {@link ApiResult} (ADR-008, ADR-015). No business logic here.
 *
 * <p>RBAC (ADR-011): every route requires the agent/fraud-analyst role - fraud-case data is sensitive
 * and not customer-self-service. This service reuses the platform's existing role taxonomy: the
 * agent-facing role is {@code SUPPORT} (the same role ticket-service gates its agent assign/resolve
 * endpoints on), with {@code ADMIN} always permitted.
 *
 * <p><strong>Detect-and-alert only (ADR-029 Section 5):</strong> none of these routes ever calls
 * subscription-service or triggers a suspend/hold - resolving a case is a {@code FraudCase} status
 * change plus an event publish only.
 */
@RestController
@RequestMapping("/api/v1/fraud-cases")
public class FraudCaseController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public FraudCaseController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ApiResult<PageResult<FraudCaseSummaryResponse>> listCases(
            @RequestParam(required = false) FraudCaseStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(
                new GetFraudCasesQuery(status, customerId, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ApiResult<FraudCaseDetailResponse> getCase(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetFraudCaseQuery(id)));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ApiResult<FraudCaseSummaryResponse> resolveCase(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveFraudCaseRequest request) {
        return responses.ok(mediator.send(
                new ResolveFraudCaseCommand(id, request.status(), request.note())));
    }
}
