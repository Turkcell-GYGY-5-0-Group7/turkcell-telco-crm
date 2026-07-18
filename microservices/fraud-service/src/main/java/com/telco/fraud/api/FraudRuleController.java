package com.telco.fraud.api;

import com.telco.fraud.application.command.UpdateFraudRuleCommand;
import com.telco.fraud.application.dto.FraudRuleResponse;
import com.telco.fraud.application.dto.UpdateFraudRuleRequest;
import com.telco.fraud.application.query.GetFraudRulesQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin rule-threshold configuration surface (Feature 23.3.3, ADR-029 Section 4). Thin edge: HTTP ->
 * query/command via {@link Mediator} -> {@link ApiResult} (ADR-008, ADR-015). No business logic here.
 *
 * <p>RBAC (ADR-011), reusing the platform's existing role taxonomy: {@code PUT} (threshold write) is
 * gated on the stricter {@code ADMIN} role; {@code GET} is available to the agent/fraud-analyst role
 * ({@code SUPPORT}), with {@code ADMIN} always permitted.
 */
@RestController
@RequestMapping("/api/v1/fraud-rules")
public class FraudRuleController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public FraudRuleController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPPORT') or hasRole('ADMIN')")
    public ApiResult<List<FraudRuleResponse>> listRules() {
        return responses.ok(mediator.query(new GetFraudRulesQuery()));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<FraudRuleResponse> updateRule(
            @PathVariable String code,
            @Valid @RequestBody UpdateFraudRuleRequest request) {
        return responses.ok(mediator.send(new UpdateFraudRuleCommand(
                code,
                request.windowMinutes(),
                request.thresholdCount(),
                request.severity(),
                request.enabled())));
    }
}
