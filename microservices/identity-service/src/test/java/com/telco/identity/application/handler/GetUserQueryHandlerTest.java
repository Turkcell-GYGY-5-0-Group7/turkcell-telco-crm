package com.telco.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.query.GetUserQuery;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserQueryHandlerTest {

    @Mock
    private UserRepository userRepository;

    private GetUserQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUserQueryHandler(userRepository);
    }

    @Test
    void returnsUserResponseForExistingId() {
        User user = User.provision("kc-dan", "dan", "dan@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = handler.handle(new GetUserQuery(user.getId()));

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.username()).isEqualTo("dan");
        assertThat(response.email()).isEqualTo("dan@example.com");
    }

    @Test
    void throwsResourceNotFoundForUnknownId() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetUserQuery(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
