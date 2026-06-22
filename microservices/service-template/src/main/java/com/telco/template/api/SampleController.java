package com.telco.template.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.template.application.EchoCommand;
import com.telco.template.application.EchoResponse;
import com.telco.template.application.PingQuery;
import com.telco.template.application.PingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample edge of the service. Controllers MUST stay thin: they translate HTTP to commands/queries,
 * dispatch through the {@link Mediator}, and wrap the result in {@link ApiResult} (ADR-004, ADR-015).
 * No business logic here.
 */
@RestController
@RequestMapping("/api/v1")
public class SampleController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public SampleController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @GetMapping("/ping")
    public ApiResult<PingResponse> ping() {
        return responses.ok(mediator.query(new PingQuery()));
    }

    @PostMapping("/echo")
    public ApiResult<EchoResponse> echo(@Valid @RequestBody EchoCommand command) {
        return responses.ok(mediator.send(command));
    }
}
