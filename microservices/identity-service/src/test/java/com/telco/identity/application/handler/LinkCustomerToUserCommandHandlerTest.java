package com.telco.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.LinkCustomerToUserCommand;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.cqrs.Unit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LinkCustomerToUserCommandHandler} (Section 14.1.1 ruling: identity-to-customer
 * linkage gap).
 */
@ExtendWith(MockitoExtension.class)
class LinkCustomerToUserCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private LinkCustomerToUserCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new LinkCustomerToUserCommandHandler(userRepository, auditLogWriter, keycloakAdminClient);
    }

    @Test
    void linksMatchingUserToCustomerAndWritesAudit() {
        User user = User.provision("kc-dora", "dora", "dora@example.com");
        UUID customerId = UUID.randomUUID();

        when(userRepository.findByKeycloakId("kc-dora")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Unit result = handler.handle(new LinkCustomerToUserCommand("kc-dora", customerId));

        assertThat(result).isEqualTo(Unit.INSTANCE);
        assertThat(user.getCustomerId()).isEqualTo(customerId);
        verify(userRepository).save(user);
        verify(auditLogWriter).log(eq("USER_CUSTOMER_LINKED"), eq("User"),
                eq(user.getId().toString()), any());
        verify(keycloakAdminClient).setCustomerIdAttribute("kc-dora", customerId);
    }

    @Test
    void noMatchingUserIsASafeNoOpAndNeverThrows() {
        UUID customerId = UUID.randomUUID();
        when(userRepository.findByKeycloakId("kc-ghost")).thenReturn(Optional.empty());

        Unit result = handler.handle(new LinkCustomerToUserCommand("kc-ghost", customerId));

        assertThat(result).isEqualTo(Unit.INSTANCE);
        verify(userRepository, never()).save(any());
        verify(auditLogWriter, never()).log(any(), any(), any(), any());
        verify(keycloakAdminClient, never()).setCustomerIdAttribute(any(), any());
    }

    @Test
    void keycloakAttributePushFailureDoesNotUndoTheLocalLink() {
        User user = User.provision("kc-flaky", "flaky", "flaky@example.com");
        UUID customerId = UUID.randomUUID();

        when(userRepository.findByKeycloakId("kc-flaky")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new DependencyFailureException("Keycloak Admin API unavailable", new RuntimeException("boom")))
                .when(keycloakAdminClient).setCustomerIdAttribute("kc-flaky", customerId);

        assertThatCode(() -> handler.handle(new LinkCustomerToUserCommand("kc-flaky", customerId)))
                .doesNotThrowAnyException();

        assertThat(user.getCustomerId()).isEqualTo(customerId);
        verify(userRepository).save(user);
        verify(auditLogWriter).log(eq("USER_CUSTOMER_LINKED"), eq("User"),
                eq(user.getId().toString()), any());
    }
}
