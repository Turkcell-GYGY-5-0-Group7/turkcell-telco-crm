package com.telco.identity.application.handler;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.DeleteUserCommand;
import com.telco.identity.application.event.UserDeletedV1;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.common.exception.CommonErrorCode;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Soft-deletes a user: marks the local projection DELETED, disables the Keycloak account (ending
 * all future logins), publishes {@code user.deleted.v1} through the transactional outbox, and writes
 * the mandatory audit record (ADR-021). The mediator TransactionBehavior makes the JPA update and
 * outbox row atomic with the Keycloak call outside the transaction boundary.
 */
@Component
public class DeleteUserCommandHandler implements CommandHandler<DeleteUserCommand, Unit> {

    private static final String AGGREGATE_TYPE = "User";
    private static final String OUTBOX_AGGREGATE_TYPE = "user";
    private static final String EVENT_TYPE = "user.deleted.v1";

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public DeleteUserCommandHandler(UserRepository userRepository,
                                    KeycloakAdminClient keycloakAdminClient,
                                    OutboxService outboxService,
                                    AuditLogWriter auditLogWriter) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public Unit handle(DeleteUserCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        CommonErrorCode.RESOURCE_NOT_FOUND, "User not found",
                        Map.of("id", command.userId().toString())));

        user.delete();
        userRepository.save(user);

        keycloakAdminClient.disableUser(user.getKeycloakId());

        String userId = user.getId().toString();
        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                userId,
                EVENT_TYPE,
                new UserDeletedV1(userId, Instant.now().toString())
        );

        auditLogWriter.log("USER_DELETED", AGGREGATE_TYPE, userId, Map.of());

        return Unit.INSTANCE;
    }
}
