package com.telco.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.query.ListUsersQuery;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.persistence.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListUsersQueryHandlerTest {

    @Mock
    private UserRepository userRepository;

    private ListUsersQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ListUsersQueryHandler(userRepository);
    }

    @Test
    void returnsAllUsersAsDtos() {
        User u1 = User.provision("kc-e1", "eve", "eve@example.com");
        User u2 = User.provision("kc-f2", "frank", "frank@example.com");
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> result = handler.handle(new ListUsersQuery());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::username)
                .containsExactlyInAnyOrder("eve", "frank");
    }

    @Test
    void returnsEmptyListWhenNoUsersExist() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<UserResponse> result = handler.handle(new ListUsersQuery());

        assertThat(result).isEmpty();
    }
}
