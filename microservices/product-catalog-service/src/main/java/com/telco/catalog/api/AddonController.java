package com.telco.catalog.api;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.application.query.ListAddonsQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Addon catalog API. Thin edge: HTTP -> query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/addons")
public class AddonController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public AddonController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /**
     * Returns a paginated list of addons. When {@code tariffCode} is provided, filters to addons
     * linked to that tariff (feature 7.4.3).
     */
    @GetMapping
    public ApiResult<PageResult<AddonResponse>> listAddons(
            @RequestParam(required = false) String tariffCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new ListAddonsQuery(tariffCode, page, size)));
    }
}
