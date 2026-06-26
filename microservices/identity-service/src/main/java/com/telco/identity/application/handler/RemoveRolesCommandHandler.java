package com.telco.identity.application.handler;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.RemoveRolesCommand;
import com.telco.identity.domain.Role;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.RoleRepository;
import com.telco.identity.infrastructure.UserRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Removes realm roles from an existing user both in Keycloak and in the local projection (FR-IAM-04).
 * All role lookups and the user save run inside the mediator TransactionBehavior transaction.
 */
@Component
public class RemoveRolesCommandHandler implements CommandHandler<RemoveRolesCommand, Unit> {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditLogWriter auditLogWriter;

    public RemoveRolesCommandHandler(UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     KeycloakAdminClient keycloakAdminClient,
                                     AuditLogWriter auditLogWriter) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(RemoveRolesCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "User not found",
                        Map.of("id", command.userId().toString())));

        for (String roleName : command.roleNames()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            CommonErrorCode.RESOURCE_NOT_FOUND, "Role not found",
                            Map.of("role", roleName)));
            user.removeRole(role);
        }

        keycloakAdminClient.removeRealmRoles(user.getKeycloakId(), command.roleNames());

        userRepository.save(user);

        auditLogWriter.log("ROLES_REMOVED", "User", command.userId().toString(),
                Map.of("roles", String.join(",", command.roleNames())));

        return Unit.INSTANCE;
    }
}
