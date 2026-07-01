package com.telco.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.CreateUserCommand;
import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private OutboxService outboxService;
    @Mock
    private AuditLogWriter auditLogWriter;

    private CreateUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateUserCommandHandler(userRepository, keycloakAdminClient, outboxService,
                auditLogWriter);
    }

    @Test
    void provisionsUserActivatesAndPublishesEvent() {
        when(keycloakAdminClient.createUser("alice", "alice@example.com")).thenReturn("kc-alice-123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = handler.handle(new CreateUserCommand("alice", "alice@example.com"));

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(outboxService).publish(eq("user"), any(), eq("user.created.v1"), any());
        verify(auditLogWriter).log(eq("USER_CREATED"), eq("User"), any(), any());
    }
}
