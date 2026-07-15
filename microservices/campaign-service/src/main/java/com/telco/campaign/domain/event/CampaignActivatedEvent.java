package com.telco.campaign.domain.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code campaign.activated.v1} (ADR-009,
 * ADR-019) when a campaign transitions DRAFT/PAUSED -&gt; ACTIVE.
 */
public record CampaignActivatedEvent(
        String campaignId,
        String code,
        String occurredAt
) implements Event {
}
