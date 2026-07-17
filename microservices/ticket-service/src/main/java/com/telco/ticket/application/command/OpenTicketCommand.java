package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

/**
 * @param externalRef correlates this ticket back to an external originating entity (e.g. a
 *                     dispute id, for {@code category = "DISPUTE"} tickets auto-opened by
 *                     {@code DisputeOpenedTicketConsumer}). {@code null} for tickets opened
 *                     directly via the HTTP API, which have no external correlation.
 */
public record OpenTicketCommand(
        UUID customerId,
        String category,
        String priority,
        String subject,
        String externalRef
) implements Command<UUID> {

    /** Original 4-arg form, preserved for every existing (non-dispute) caller - externalRef defaults to null. */
    public OpenTicketCommand(UUID customerId, String category, String priority, String subject) {
        this(customerId, category, priority, subject, null);
    }
}
