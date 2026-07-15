package com.telco.campaign.application.query;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

/** Returns a paginated list of all campaigns, regardless of status (admin listing). */
public record ListCampaignsQuery(int page, int size) implements Query<PageResult<CampaignResponse>> {
}
