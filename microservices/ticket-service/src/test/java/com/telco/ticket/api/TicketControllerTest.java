package com.telco.ticket.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.context.CurrentUserProvider;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.ticket.api.dto.TicketResponse;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.application.query.GetTicketQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No business logic lives here (ADR-008) — confirms the controller resolves the caller's linked
 * {@code customerId} claim (identity-to-customer linkage, ADR-011) via {@link CurrentUserProvider}
 * and passes it into the query alongside the raw caller id and admin flag.
 */
@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock private Mediator mediator;
    @Mock private ApiResponseFactory apiResponseFactory;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private Authentication subscriberAuth;

    private TicketController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketController(mediator, apiResponseFactory, currentUserProvider);
    }

    @Test
    void getTicket_passes_resolved_customer_id_claim_into_the_query() {
        UUID ticketId = UUID.randomUUID();
        UUID callerSub = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn(callerSub.toString());
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(callerSub.toString(), Set.of("SUBSCRIBER"), null,
                        customerId.toString()));
        TicketResponse ticketResponse = new TicketResponse(
                ticketId, customerId, "BILLING", "HIGH", "OPEN", "billing-support",
                "subject", null, Instant.now().plusSeconds(3600), false, Instant.now(), null, List.of());
        when(mediator.query(new GetTicketQuery(ticketId, callerSub, false, customerId.toString())))
                .thenReturn(ticketResponse);
        when(apiResponseFactory.ok(ticketResponse)).thenReturn(ApiResult.ok(ticketResponse, null));

        ApiResult<TicketResponse> response = controller.getTicket(ticketId, subscriberAuth);

        assertThat(response.data()).isEqualTo(ticketResponse);
    }

    @Test
    void openTicket_uses_the_resolved_customer_id_claim_not_the_raw_keycloak_subject() {
        UUID callerSub = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(callerSub.toString(), Set.of("SUBSCRIBER"), null,
                        customerId.toString()));
        when(mediator.send(new OpenTicketCommand(customerId, "BILLING", "HIGH", "Invoice error")))
                .thenReturn(ticketId);
        when(apiResponseFactory.ok(ticketId)).thenReturn(ApiResult.ok(ticketId, null));

        ApiResult<UUID> response = controller.openTicket(
                new TicketController.OpenTicketRequest("BILLING", "HIGH", "Invoice error"));

        assertThat(response.data()).isEqualTo(ticketId);
    }

    @Test
    void openTicket_denies_an_unlinked_caller_and_never_dispatches_the_command() {
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(UUID.randomUUID().toString(), Set.of("SUBSCRIBER"), null, null));

        assertThatThrownBy(() -> controller.openTicket(
                new TicketController.OpenTicketRequest("BILLING", "HIGH", "Invoice error")))
                .isInstanceOf(AccessDeniedException.class);

        verify(mediator, never()).send(org.mockito.ArgumentMatchers.any(OpenTicketCommand.class));
    }

    @Test
    void getTicket_passes_null_customer_id_when_caller_is_unlinked() {
        UUID ticketId = UUID.randomUUID();
        UUID callerSub = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(subscriberAuth.getName()).thenReturn(callerSub.toString());
        doReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_SUBSCRIBER")))
                .when(subscriberAuth).getAuthorities();
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(callerSub.toString(), Set.of("SUBSCRIBER"), null, null));
        TicketResponse ticketResponse = new TicketResponse(
                ticketId, customerId, "BILLING", "HIGH", "OPEN", "billing-support",
                "subject", null, Instant.now().plusSeconds(3600), false, Instant.now(), null, List.of());
        when(mediator.query(new GetTicketQuery(ticketId, callerSub, false, null)))
                .thenReturn(ticketResponse);
        when(apiResponseFactory.ok(ticketResponse)).thenReturn(ApiResult.ok(ticketResponse, null));

        ApiResult<TicketResponse> response = controller.getTicket(ticketId, subscriberAuth);

        assertThat(response.data()).isEqualTo(ticketResponse);
    }
}
