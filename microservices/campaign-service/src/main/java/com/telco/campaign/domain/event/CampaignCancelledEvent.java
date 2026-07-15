package com.telco.campaign.domain.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code campaign.cancelled.v1} (ADR-009,
 * ADR-019) when a campaign transitions any non-terminal status -&gt; CANCELLED.
 */
public record CampaignCancelledEvent(
        String campaignId,
        String code,
        String occurredAt
) implements Event {
}
