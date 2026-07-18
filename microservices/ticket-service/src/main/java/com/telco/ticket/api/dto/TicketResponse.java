package com.telco.ticket.api.dto;

import com.telco.ticket.domain.Ticket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID customerId,
        String category,
        String priority,
        String status,
        String assignedTeam,
        String subject,
        String externalRef,
        Instant slaDueAt,
        boolean slaBreached,
        Instant createdAt,
        Instant resolvedAt,
        List<TicketCommentResponse> comments
) {
    public static TicketResponse from(Ticket t) {
        boolean breached = t.getSlaDueAt() != null
                && t.getStatus().name().equals("OPEN") || t.getStatus().name().equals("ASSIGNED")
                && Instant.now().isAfter(t.getSlaDueAt());
        return new TicketResponse(
                t.getId(), t.getCustomerId(), t.getCategory(), t.getPriority(),
                t.getStatus().name(), t.getAssignedTeam(), t.getSubject(),
                t.getExternalRef(), t.getSlaDueAt(), breached,
                t.getCreatedAt(), t.getResolvedAt(),
                t.getComments().stream().map(TicketCommentResponse::from).toList()
        );
    }
}
