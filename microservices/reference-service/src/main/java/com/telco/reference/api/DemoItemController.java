package com.telco.reference.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.reference.application.CreateDemoItemCommand;
import com.telco.reference.application.DemoItemResponse;
import com.telco.reference.application.GetDemoItemQuery;
import com.telco.reference.application.ListDemoItemsQuery;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Demo items API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/demo-items")
public class DemoItemController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public DemoItemController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<DemoItemResponse> create(@Valid @RequestBody CreateDemoItemCommand command) {
        return responses.ok(mediator.send(command));
    }

    @GetMapping("/{id}")
    public ApiResult<DemoItemResponse> get(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetDemoItemQuery(id)));
    }

    @GetMapping
    public ApiResult<List<DemoItemResponse>> list() {
        return responses.ok(mediator.query(new ListDemoItemsQuery()));
    }
}
