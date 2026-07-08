package com.telco.ticket.application.query;

import com.telco.platform.cqrs.Query;
import com.telco.ticket.api.dto.TicketResponse;
import java.util.UUID;

/**
 * @param callerUserId     raw JWT subject; retained for audit/logging only, no longer used for the
 *                         ownership check (identity-to-customer linkage, ADR-011)
 * @param callerIsAdmin    staff bypass; preserved as-is
 * @param callerCustomerId resolved {@code customerId} claim linked to the caller's identity; null
 *                         when the caller is staff or the identity is not yet linked
 */
public record GetTicketQuery(UUID ticketId, UUID callerUserId, boolean callerIsAdmin,
                             String callerCustomerId)
        implements Query<TicketResponse> {}
