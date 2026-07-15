package com.telco.campaign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * campaign-service entry point.
 *
 * <p>Architecture Mode: CQRS + MEDIATOR (ADR-004, ADR-027 Decision Section 2). Platform
 * capabilities (mediator, API contract, security, observability, outbox, inbox) are contributed by
 * platform starters via auto-configuration; this class only bootstraps Spring Boot.
 *
 * <p>Domain behavior (21.2), the validate API (21.3), and eventing/outbox-inbox wiring (21.4) -
 * including the {@code @Scheduled} reservation-expiry reaper, activated by
 * {@code infrastructure.scheduler.SchedulerConfig}'s {@code @EnableScheduling} - are all now wired.
 */
@SpringBootApplication
public class CampaignServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampaignServiceApplication.class, args);
    }
}
