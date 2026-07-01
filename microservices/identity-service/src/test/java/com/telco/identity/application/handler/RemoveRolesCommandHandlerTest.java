package com.telco.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.RemoveRolesCommand;
import com.telco.identity.domain.Role;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.RoleRepository;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.Unit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoveRolesCommandHandlerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private AuditLogWriter auditLogWriter;

    private RemoveRolesCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RemoveRolesCommandHandler(userRepository, roleRepository, keycloakAdminClient,
                auditLogWriter);
    }

    @Test
    void removesRolesFromUserInBothProjectionAndKeycloak() {
        Role adminRole = Role.of("ADMIN");
        User user = User.provision("kc-carol", "carol", "carol@example.com");
        user.assignRole(adminRole);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Unit result = handler.handle(new RemoveRolesCommand(user.getId(), Set.of("ADMIN")));

        assertThat(result).isEqualTo(Unit.INSTANCE);
        assertThat(user.getRoles()).doesNotContain(adminRole);
        verify(keycloakAdminClient).removeRealmRoles(eq("kc-carol"), eq(Set.of("ADMIN")));
        verify(auditLogWriter).log(eq("ROLES_REMOVED"), eq("User"),
                eq(user.getId().toString()), any());
    }

    @Test
    void throwsResourceNotFoundWhenUserDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RemoveRolesCommand(missing, Set.of("ADMIN"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throwsResourceNotFoundWhenRoleDoesNotExist() {
        User user = User.provision("kc-carol", "carol", "carol@example.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findByName("GHOST_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(
                new RemoveRolesCommand(user.getId(), Set.of("GHOST_ROLE"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
