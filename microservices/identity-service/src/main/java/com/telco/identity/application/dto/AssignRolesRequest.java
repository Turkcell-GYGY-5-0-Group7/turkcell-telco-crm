package com.telco.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Request body for assigning roles to a user. */
public record AssignRolesRequest(@NotEmpty Set<@NotBlank String> roleNames) {
}
