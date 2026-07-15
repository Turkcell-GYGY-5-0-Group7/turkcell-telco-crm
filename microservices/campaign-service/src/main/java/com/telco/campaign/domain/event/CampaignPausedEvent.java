package com.telco.campaign.domain.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code campaign.paused.v1} (ADR-009, ADR-019)
 * when a campaign transitions ACTIVE -&gt; PAUSED.
 */
public record CampaignPausedEvent(
        String campaignId,
        String code,
        String occurredAt
) implements Event {
}
