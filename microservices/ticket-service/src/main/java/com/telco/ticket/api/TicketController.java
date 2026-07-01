package com.telco.ticket.api;

import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import com.telco.ticket.api.dto.TicketResponse;
import com.telco.ticket.application.command.AddCommentCommand;
import com.telco.ticket.application.command.AssignTicketCommand;
import com.telco.ticket.application.command.OpenTicketCommand;
import com.telco.ticket.application.command.ResolveTicketCommand;
import com.telco.ticket.application.query.GetTicketQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final Mediator mediator;
    private final ApiResponseFactory apiResponseFactory;

    public TicketController(Mediator mediator, ApiResponseFactory apiResponseFactory) {
        this.mediator = mediator;
        this.apiResponseFactory = apiResponseFactory;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<?> openTicket(@Valid @RequestBody OpenTicketRequest request,
                                         Authentication auth) {
        UUID ticketId = mediator.send(new OpenTicketCommand(
                UUID.fromString(auth.getName()),
                request.category(), request.priority(), request.subject()));
        return ResponseEntity.ok(apiResponseFactory.ok(ticketId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<?> getTicket(@PathVariable UUID id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        UUID callerId = UUID.fromString(auth.getName());
        TicketResponse response = mediator.query(new GetTicketQuery(id, callerId, isAdmin));
        return ResponseEntity.ok(apiResponseFactory.ok(response));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ResponseEntity<?> addComment(@PathVariable UUID id,
                                         @Valid @RequestBody CommentRequest request,
                                         Authentication auth) {
        UUID commentId = mediator.send(new AddCommentCommand(id,
                UUID.fromString(auth.getName()), request.body()));
        return ResponseEntity.ok(apiResponseFactory.ok(commentId));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<?> assignTicket(@PathVariable UUID id,
                                           @Valid @RequestBody AssignRequest request) {
        mediator.send(new AssignTicketCommand(id, request.team()));
        return ResponseEntity.ok(apiResponseFactory.ok(null));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<?> resolveTicket(@PathVariable UUID id) {
        mediator.send(new ResolveTicketCommand(id));
        return ResponseEntity.ok(apiResponseFactory.ok(null));
    }

    public record OpenTicketRequest(
            @NotBlank String category,
            @NotBlank String priority,
            @NotBlank String subject) {}

    public record CommentRequest(@NotBlank String body) {}

    public record AssignRequest(@NotBlank String team) {}
}
