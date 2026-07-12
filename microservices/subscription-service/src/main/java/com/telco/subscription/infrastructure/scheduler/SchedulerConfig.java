package com.telco.subscription.infrastructure.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activates {@code @Scheduled} beans (feature 17.3's {@link MsisdnReservationExpiryReaper}). */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
