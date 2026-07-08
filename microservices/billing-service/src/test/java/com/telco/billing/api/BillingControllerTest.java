package com.telco.billing.api;

import com.telco.billing.api.dto.BillRunRequest;
import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** No business logic lives here (ADR-008) — this just confirms the controller wires the request
 *  into the mediator and wraps the result, without bypassing it. */
@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock private Mediator mediator;
    @Mock private ApiResponseFactory responses;

    private BillingController controller;

    @BeforeEach
    void setUp() {
        controller = new BillingController(mediator, responses);
    }

    @Test
    void triggerBillRun_sends_run_bill_command_through_the_mediator() {
        Instant periodStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant periodEnd = Instant.parse("2026-08-01T00:00:00Z");
        RunBillResult result = new RunBillResult(10, 2);
        when(mediator.send(new RunBillCommand(periodStart, periodEnd))).thenReturn(result);
        when(responses.ok(result)).thenReturn(ApiResult.ok(result, null));

        ApiResult<RunBillResult> response = controller.triggerBillRun(new BillRunRequest(periodStart, periodEnd));

        assertThat(response.data()).isEqualTo(result);
        verify(mediator).send(new RunBillCommand(periodStart, periodEnd));
    }
}
