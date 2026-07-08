package com.telco.catalog.api;

import com.telco.catalog.application.dto.AllowanceSnapshotResponse;
import com.telco.catalog.application.dto.PriceSnapshotResponse;
import com.telco.catalog.application.dto.TariffResponse;
import com.telco.catalog.application.query.GetTariffAllowanceSnapshotQuery;
import com.telco.catalog.application.query.GetTariffByIdQuery;
import com.telco.catalog.application.query.GetTariffPriceSnapshotQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal, system-to-system tariff reads for other services' synchronous hops
 * (order-service's tariff-by-id lookup, billing-service's price snapshot, usage-service's
 * allowance snapshot for quota provisioning). No PII, no ADMIN-only data.
 *
 * <p>Trusted endpoint (tech-lead ruling 2026-07-06, tariff endpoint internal-surface fix): NO JWT
 * requirement - permitted in {@link CatalogSecurityConfig} and the gateway excludes
 * {@code /internal/**} from public traffic (blocked by {@code internal-deny-route}, handled by
 * devops). These three routes previously lived on the public {@code /api/v1/tariffs} surface as
 * {@code permitAll}, which made them genuinely internet-reachable since only {@code /internal/**}
 * is firewalled at the gateway - moving them here closes that gap (ADR-011).
 */
@RestController
@RequestMapping("/internal/tariffs")
public class TariffInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public TariffInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /**
     * Returns a single active tariff by its primary key. Internal lookup for callers (e.g.
     * order-service) that hold the tariff's UUID rather than its human-readable code. Returns 404
     * if not found or not active.
     */
    @GetMapping("/{id}")
    public ApiResult<TariffResponse> getTariffById(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetTariffByIdQuery(id)));
    }

    /** Returns a lightweight price snapshot for the given tariff code (billing-service). */
    @GetMapping("/{code}/price-snapshot")
    public ApiResult<PriceSnapshotResponse> getPriceSnapshot(@PathVariable String code) {
        return responses.ok(mediator.query(new GetTariffPriceSnapshotQuery(code)));
    }

    /**
     * Returns a lightweight usage-allowance snapshot for the given tariff code (usage-service's
     * quota provisioning, triggered from a Kafka consumer with no caller JWT to forward).
     */
    @GetMapping("/{code}/allowance-snapshot")
    public ApiResult<AllowanceSnapshotResponse> getAllowanceSnapshot(@PathVariable String code) {
        return responses.ok(mediator.query(new GetTariffAllowanceSnapshotQuery(code)));
    }
}
