package com.telco.campaign.application.query;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Fetches a single campaign by its primary key. Returns 404 if not found. */
public record GetCampaignQuery(UUID id) implements Query<CampaignResponse> {
}
