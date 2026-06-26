package com.telco.identity.application.query;

import com.telco.identity.application.dto.UserResponse;
import com.telco.platform.cqrs.Query;

import java.util.List;

/** Returns all users in the identity projection. */
public record ListUsersQuery() implements Query<List<UserResponse>> {
}
