package com.telco.identity.application.dto;

import com.telco.identity.domain.Role;
import com.telco.identity.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read DTO for a user identity projection. Domain entities are never exposed directly (ADR-015). */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String status,
        Set<String> roles,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().name(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
