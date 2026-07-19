package com.telco.catalog.api;

import com.telco.catalog.application.dto.AddonSnapshotResponse;
import com.telco.catalog.application.query.GetAddonSnapshotQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, system-to-system addon reads (order-service's addon snapshot for ADDON order
 * pricing, feature 24.1). No PII, no ADMIN-only data.
 *
 * <p>Trusted endpoint, mirroring {@link TariffInternalController}: NO JWT requirement -
 * {@code /internal/**} is permitAll in {@link CatalogSecurityConfig} and the gateway blocks
 * {@code /internal/**} from public traffic (internal-deny-route, handled by devops), so the
 * route is reachable in-network only (ADR-011).
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

    /**
     * Returns a lightweight snapshot (price, currency, validity, allowances) for the given addon
     * code. Returns 404 if the addon does not exist or is not ACTIVE.
     */
    @GetMapping("/{code}/snapshot")
    public ApiResult<AddonSnapshotResponse> getAddonSnapshot(@PathVariable String code) {
        return responses.ok(mediator.query(new GetAddonSnapshotQuery(code)));
    }
}
