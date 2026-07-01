package com.telco.ticket.application.query;

import com.telco.platform.cqrs.Query;
import com.telco.ticket.api.dto.TicketResponse;
import java.util.UUID;

public record GetTicketQuery(UUID ticketId, UUID callerUserId, boolean callerIsAdmin)
        implements Query<TicketResponse> {}
