package com.telco.campaign.infrastructure.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activates {@code @Scheduled} beans (Feature 21.4's {@link CampaignRedemptionReservationExpiryReaper}). */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
