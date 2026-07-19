package com.telco.campaign.application.handler;

import com.telco.campaign.application.dto.CampaignValidationResponse;
import com.telco.campaign.application.query.ValidateCampaignQuery;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.domain.model.CampaignStatus;
import com.telco.campaign.domain.model.EligibilityDecision;
import com.telco.campaign.domain.model.EligibilityReason;
import com.telco.campaign.domain.service.CampaignEligibilityService;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles {@link ValidateCampaignQuery} (Feature 21.3.1): the HTTP-facing entry point for the
 * eligibility+discount decision order-service needs before pricing a discounted {@code OrderItem}
 * (ADR-027 Decision Section 4).
 *
 * <p>When {@code campaignCode} is supplied, delegates straight to
 * {@link CampaignEligibilityService#evaluate}. When omitted, first resolves the best-matching ACTIVE
 * campaign for {@code tariffCode} via {@link CampaignRepository#findByStatusAndApplicableTariffCode}
 * (tie-break: highest {@code discountValue}, see that method's javadoc), then evaluates against the
 * resolved candidate's code - re-running the full validity-window/redemption-cap check rather than
 * assuming the ACTIVE+tariff-applicable filter alone is sufficient.
 *
 * <p>Read-only end to end: {@link CampaignEligibilityService#evaluate} never creates or mutates a
 * {@code CampaignRedemption} row (reservation happens only via Feature 21.4's {@code order.created.v1}
 * consumption).
 */
@Component
public class ValidateCampaignQueryHandler
        implements QueryHandler<ValidateCampaignQuery, CampaignValidationResponse> {

    private final CampaignEligibilityService eligibilityService;
    private final CampaignRepository campaignRepository;

    public ValidateCampaignQueryHandler(CampaignEligibilityService eligibilityService,
                                         CampaignRepository campaignRepository) {
        this.eligibilityService = eligibilityService;
        this.campaignRepository = campaignRepository;
    }

    @Override
    public CampaignValidationResponse handle(ValidateCampaignQuery query) {
        String campaignCode = query.campaignCode();

        if (campaignCode == null || campaignCode.isBlank()) {
            List<Campaign> candidates = campaignRepository.findByStatusAndApplicableTariffCode(
                    CampaignStatus.ACTIVE, query.tariffCode());
            if (candidates.isEmpty()) {
                return CampaignValidationResponse.from(
                        EligibilityDecision.ineligible(EligibilityReason.NO_MATCHING_CAMPAIGN));
            }
            // Tie-break: highest discountValue first (query orders DESC); take the best match.
            campaignCode = candidates.get(0).getCode();
        }

        EligibilityDecision decision =
                eligibilityService.evaluate(campaignCode, query.customerId(), query.tariffCode());
        return CampaignValidationResponse.from(decision);
    }
}
