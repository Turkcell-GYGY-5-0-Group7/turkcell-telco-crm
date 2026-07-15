package com.telco.campaign.application.handler;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.campaign.application.query.ListCampaignsQuery;
import com.telco.campaign.infrastructure.persistence.CampaignRepository;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns a paginated list of all campaigns regardless of status. This is the admin-facing listing
 * surface (Feature 21.2.1) - not the eligibility-validation read path (21.3), which resolves a single
 * campaign by code via {@code CampaignEligibilityService}. {@code @Transactional(readOnly = true)} is
 * required for the same lazy-collection reason as {@link GetCampaignQueryHandler}.
 */
@Component
public class ListCampaignsQueryHandler
        implements QueryHandler<ListCampaignsQuery, PageResult<CampaignResponse>> {

    private final CampaignRepository campaignRepository;

    public ListCampaignsQueryHandler(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CampaignResponse> handle(ListCampaignsQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());

        Page<CampaignResponse> page = campaignRepository.findAll(pageable)
                .map(CampaignResponse::from);

        return new PageResult<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
