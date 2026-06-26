package com.telco.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/** Request body for removing roles from a user. */
public record RemoveRolesRequest(@NotEmpty Set<@NotBlank String> roleNames) {
}
