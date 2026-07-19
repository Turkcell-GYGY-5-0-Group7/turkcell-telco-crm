package com.telco.campaign.application.command;

import com.telco.campaign.application.dto.CampaignResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Transitions a campaign ACTIVE -&gt; PAUSED (Feature 21.2.1). */
public record PauseCampaignCommand(UUID id) implements Command<CampaignResponse> {
}
