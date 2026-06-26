package com.telco.identity.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/** Removes one or more realm roles from an existing user (FR-IAM-04). */
public record RemoveRolesCommand(
        @NotNull UUID userId,
        @NotEmpty Set<@NotBlank String> roleNames
) implements Command<Unit> {
}
