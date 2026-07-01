package com.telco.ticket.infrastructure.config;

import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaBreachScheduler {

    private final Mediator mediator;

    public SlaBreachScheduler(Mediator mediator) {
        this.mediator = mediator;
    }

    @Scheduled(fixedDelayString = "${ticket.sla-breach-check-ms:300000}")
    public void checkSlaBreaches() {
        mediator.send(new DetectSlaBreachCommand());
    }
}
