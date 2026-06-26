package com.telco.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates Spring's task scheduling support for the payment retry scheduler
 * ({@link com.telco.payment.application.scheduler.PaymentRetryScheduler}).
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
