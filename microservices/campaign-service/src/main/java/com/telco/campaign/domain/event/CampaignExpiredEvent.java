package com.telco.campaign.domain.event;

import com.telco.platform.cqrs.Event;

/**
 * Versioned event payload published to the outbox as {@code campaign.expired.v1} (ADR-009, ADR-019)
 * when a campaign transitions ACTIVE/PAUSED -&gt; EXPIRED, either from an explicit admin action or
 * defensively (auto-expire when {@code validTo} has passed, {@code CampaignEligibilityService}).
 */
public record CampaignExpiredEvent(
        String campaignId,
        String code,
        String occurredAt
) implements Event {
}
