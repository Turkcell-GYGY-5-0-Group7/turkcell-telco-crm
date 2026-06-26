package com.telco.identity.application.handler;

import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.query.GetUserQuery;
import com.telco.identity.infrastructure.UserRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reads a single user by id. A missing id raises {@link ResourceNotFoundException}, which
 * starter-api maps to HTTP 404 (ADR-015).
 */
@Component
public class GetUserQueryHandler implements QueryHandler<GetUserQuery, UserResponse> {

    private final UserRepository userRepository;

    public GetUserQueryHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse handle(GetUserQuery query) {
        return userRepository.findById(query.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "User not found",
                        Map.of("id", query.id().toString())));
    }
}
