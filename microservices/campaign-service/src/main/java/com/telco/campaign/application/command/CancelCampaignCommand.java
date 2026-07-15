package com.telco.campaign.application.command;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Transitions a campaign to CANCELLED from any non-terminal status (Feature 21.2.1). */
public record CancelCampaignCommand(UUID id) implements Command<CampaignResponse> {
}
