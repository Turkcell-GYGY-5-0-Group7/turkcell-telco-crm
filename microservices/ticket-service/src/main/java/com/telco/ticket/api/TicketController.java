package com.telco.ticket.api;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.cqrs.Unit;
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
    public ApiResult<UUID> openTicket(@Valid @RequestBody OpenTicketRequest request,
                                      Authentication auth) {
        return apiResponseFactory.ok(mediator.send(new OpenTicketCommand(
                UUID.fromString(auth.getName()),
                request.category(), request.priority(), request.subject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<TicketResponse> getTicket(@PathVariable UUID id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return apiResponseFactory.ok(mediator.query(
                new GetTicketQuery(id, UUID.fromString(auth.getName()), isAdmin)));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    public ApiResult<UUID> addComment(@PathVariable UUID id,
                                      @Valid @RequestBody CommentRequest request,
                                      Authentication auth) {
        return apiResponseFactory.ok(mediator.send(new AddCommentCommand(id,
                UUID.fromString(auth.getName()), request.body())));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ApiResult<Unit> assignTicket(@PathVariable UUID id,
                                        @Valid @RequestBody AssignRequest request) {
        mediator.send(new AssignTicketCommand(id, request.team()));
        return apiResponseFactory.ok(Unit.INSTANCE);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ApiResult<Unit> resolveTicket(@PathVariable UUID id) {
        mediator.send(new ResolveTicketCommand(id));
        return apiResponseFactory.ok(Unit.INSTANCE);
    }

    public record OpenTicketRequest(
            @NotBlank String category,
            @NotBlank String priority,
            @NotBlank String subject) {}

    public record CommentRequest(@NotBlank String body) {}

    public record AssignRequest(@NotBlank String team) {}
}
