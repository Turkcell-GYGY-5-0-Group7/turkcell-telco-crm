package com.telco.campaign.application.command;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Transitions a campaign DRAFT/PAUSED -&gt; ACTIVE (Feature 21.2.1). */
public record ActivateCampaignCommand(UUID id) implements Command<CampaignResponse> {
}
