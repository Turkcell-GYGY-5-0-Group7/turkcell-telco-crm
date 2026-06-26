package com.telco.identity.application.query;

import com.telco.identity.application.dto.UserResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/** Fetches a single user by id. */
public record GetUserQuery(UUID id) implements Query<UserResponse> {
}
