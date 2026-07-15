package com.telco.campaign.application.handler;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.application.query.GetCampaignQuery;
import com.telco.campaign.domain.model.Campaign;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Returns a single campaign by its primary key. Raises 404 (via ResourceNotFoundException) if
 * missing. {@code @Transactional(readOnly = true)} is required here: the {@code Mediator}'s
 * {@code TransactionBehavior} only wraps commands, not queries (see its javadoc), so without an
 * explicit transaction the Hibernate session backing {@code campaign.applicableTariffCodes} (a LAZY
 * {@code @ElementCollection}) would already be closed by the time {@code CampaignResponse.from}
 * copies it - matches the fix pattern in {@code docs/tasks/lessons.md} (2026-07-06 entry).
 */
@Component
public class GetCampaignQueryHandler implements QueryHandler<GetCampaignQuery, CampaignResponse> {

    private final CampaignRepository campaignRepository;

    public GetCampaignQueryHandler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignResponse handle(GetCampaignQuery query) {
        Campaign campaign = campaignRepository.findById(query.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND,
                        "Campaign not found with id: " + query.id(),
                        Map.of("id", query.id().toString())));

        return CampaignResponse.from(campaign);
    }
}
