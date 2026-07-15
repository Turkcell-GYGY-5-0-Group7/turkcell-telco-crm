package com.telco.campaign.api;

import com.telco.campaign.application.dto.CampaignValidationRequest;
import com.telco.campaign.application.dto.CampaignValidationResponse;
import com.telco.campaign.application.query.ValidateCampaignQuery;
import com.telco.campaign.infrastructure.config.CampaignSecurityConfig;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, system-to-system campaign validation for order-service's order-creation hot path
 * (Feature 21.3.1, ADR-027 Decision Section 4).
 *
 * <p>Trusted endpoint (tech-lead ruling 2026-07-13, ADR-027 second ratification addendum): NO JWT
 * requirement - permitted in {@link CampaignSecurityConfig} and the gateway excludes
 * {@code /internal/**} from public traffic (network-perimeter trust), mirroring
 * {@code product-catalog-service}'s {@code TariffInternalController}/{@code CatalogSecurityConfig}
 * verbatim (tech-lead ruling 2026-07-06). Never gateway-routed (Feature 21.1.3 registers no route for
 * campaign-service); called only by order-service's {@code CampaignServiceClient}.
 *
 * <p>Read-only: the underlying {@code CampaignEligibilityService.evaluate(...)} never creates or
 * mutates a {@code CampaignRedemption} row - reservation happens only via Feature 21.4's
 * {@code order.created.v1} consumption.
 */
@RestController
@RequestMapping("/internal/campaigns")
public class CampaignInternalController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CampaignInternalController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    /**
     * Returns the eligibility/discount decision for {@code request.customerId()} against
     * {@code request.tariffCode()} (and, if given, a specific {@code request.campaignCode()};
     * otherwise the best-matching ACTIVE campaign for the tariff is auto-resolved). Always 200 for a
     * well-formed request - an ineligible outcome is carried in the response body's
     * {@code eligible: false} + {@code reason}, never a 4xx/5xx.
     */
    @PostMapping("/validate")
    public ApiResult<CampaignValidationResponse> validate(
            @Valid @RequestBody CampaignValidationRequest request) {
        return responses.ok(mediator.query(new ValidateCampaignQuery(
                request.customerId(), request.tariffCode(), request.campaignCode())));
    }
}
