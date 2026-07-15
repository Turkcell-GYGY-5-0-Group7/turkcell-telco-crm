package com.telco.campaign.application.command;

/** Result of {@link ExpireCampaignRedemptionReservationsCommand}: how many holds were released. */
public record ExpireCampaignRedemptionReservationsResult(int releasedCount) {
}
