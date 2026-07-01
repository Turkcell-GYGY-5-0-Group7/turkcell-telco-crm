package com.telco.identity.application.handler;

import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.query.ListUsersQuery;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.cqrs.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Returns all users in the identity projection as a list of read DTOs. */
@Component
public class ListUsersQueryHandler implements QueryHandler<ListUsersQuery, List<UserResponse>> {

    private final UserRepository userRepository;

    public ListUsersQueryHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<UserResponse> handle(ListUsersQuery query) {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
