package com.telco.billing.infrastructure.config;

import com.telco.billing.application.command.MarkInvoicesOverdueCommand;
import com.telco.platform.mediator.Mediator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingSchedulerTest {

    @Mock private Mediator mediator;

    @Test
    void markOverdueInvoices_delegates_to_the_mediator() {
        when(mediator.send(new MarkInvoicesOverdueCommand())).thenReturn(3);

        new BillingScheduler(mediator).markOverdueInvoices();

        verify(mediator).send(new MarkInvoicesOverdueCommand());
    }
}
