package com.telco.campaign.application.command;

import com.telco.platform.cqrs.Command;

/**
 * Sweeps expired {@code RESERVED} {@code CampaignRedemption} holds back to {@code RELEASED} (Feature
 * 21.4, ADR-027 Section 4 ratification amendment). Dispatched by
 * {@link com.telco.campaign.infrastructure.scheduler.CampaignRedemptionReservationExpiryReaper} under
 * a {@code DistributedLock} so exactly one replica performs a given tick's sweep - mirrors
 * subscription-service's {@code ExpireMsisdnReservationsCommand} (Feature 17.3) exactly.
 */
public record ExpireCampaignRedemptionReservationsCommand()
        implements Command<ExpireCampaignRedemptionReservationsResult> {
}
