package com.telco.identity.api;

import com.telco.identity.application.command.AssignRolesCommand;
import com.telco.identity.application.command.CreateUserCommand;
import com.telco.identity.application.command.DeleteUserCommand;
import com.telco.identity.application.command.RemoveRolesCommand;
import com.telco.identity.application.dto.AssignRolesRequest;
import com.telco.identity.application.dto.RemoveRolesRequest;
import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.query.GetUserQuery;
import com.telco.identity.application.query.ListUsersQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User management API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public UserController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<UserResponse> createUser(@Valid @RequestBody CreateUserCommand command) {
        return responses.ok(mediator.send(command));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public ApiResult<UserResponse> getUser(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetUserQuery(id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<UserResponse>> listUsers() {
        return responses.ok(mediator.query(new ListUsersQuery()));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Unit> assignRoles(@PathVariable UUID id,
                                       @Valid @RequestBody AssignRolesRequest request) {
        return responses.ok(mediator.send(new AssignRolesCommand(id, request.roleNames())));
    }

    @DeleteMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Unit> removeRoles(@PathVariable UUID id,
                                       @Valid @RequestBody RemoveRolesRequest request) {
        return responses.ok(mediator.send(new RemoveRolesCommand(id, request.roleNames())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Unit> deleteUser(@PathVariable UUID id) {
        return responses.ok(mediator.send(new DeleteUserCommand(id)));
    }
}
