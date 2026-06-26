package com.telco.identity.application.command;

import com.telco.identity.application.dto.UserResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Provisions a new user in Keycloak and creates the local identity projection (FR-IAM-03). */
public record CreateUserCommand(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Email @Size(max = 320) String email
) implements Command<UserResponse> {
}
