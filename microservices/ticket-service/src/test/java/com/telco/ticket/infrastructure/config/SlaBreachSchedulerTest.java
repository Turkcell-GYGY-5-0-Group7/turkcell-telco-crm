package com.telco.ticket.infrastructure.config;

import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.DetectSlaBreachCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SlaBreachSchedulerTest {

    @Mock private Mediator mediator;

    private SlaBreachScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SlaBreachScheduler(mediator);
    }

    @Test
    void check_sla_breaches_delegates_detect_command_to_mediator() {
        scheduler.checkSlaBreaches();

        verify(mediator).send(any(DetectSlaBreachCommand.class));
    }
}
