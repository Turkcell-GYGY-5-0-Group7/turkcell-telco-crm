package com.telco.ticket.application.command;

import com.telco.platform.cqrs.Command;
import java.util.UUID;

/**
 * @param externalRef correlates this ticket back to an external originating entity: a dispute id
 *                     for {@code category = "DISPUTE"} tickets auto-opened by
 *                     {@code DisputeOpenedTicketConsumer}, or a fraud case id for
 *                     {@code category = "FRAUD_REVIEW"} tickets auto-opened by the fraud
 *                     auto-ticket consumer. {@code null} for tickets opened directly via the HTTP
 *                     API, which have no external correlation.
 */
public record OpenTicketCommand(
        UUID customerId,
        String category,
        String priority,
        String subject,
        String externalRef
) implements Command<UUID> {

    /**
     * Agent/API-opened ticket with no cross-context source reference. Preserves the original
     * four-argument call sites (controller, existing tests); the dispute auto-ticket consumer uses
     * the five-argument form to carry the originating dispute id, and the fraud auto-ticket consumer
     * uses it to carry the originating fraud {@code caseId}, both as {@code externalRef}.
     */
    public OpenTicketCommand(UUID customerId, String category, String priority, String subject) {
        this(customerId, category, priority, subject, null);
    }
}
