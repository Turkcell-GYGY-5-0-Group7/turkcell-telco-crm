package com.telco.catalog.api;

import com.telco.catalog.application.dto.AddonResponse;
import com.telco.catalog.application.query.GetAddonByCodeQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, system-to-system addon reads (FR-09: order-service prices ADDON orders from here).
 * Same trust model as {@link TariffInternalController}: no JWT, permitted via {@code /internal/**}
 * in {@link CatalogSecurityConfig}, and the gateway blocks {@code /internal/**} from public traffic.
 */
@RestController
@RequestMapping("/internal/addons")
public class AddonInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public AddonInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /** Returns a single addon by its unique code; 404 when unknown. */
    @GetMapping("/{code}")
    public ApiResult<AddonResponse> getAddonByCode(@PathVariable String code) {
        return responses.ok(mediator.query(new GetAddonByCodeQuery(code)));
    }
}
