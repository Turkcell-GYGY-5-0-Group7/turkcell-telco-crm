package com.telco.identity.application.handler;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.CreateUserCommand;
import com.telco.identity.application.dto.UserResponse;
import com.telco.identity.application.event.UserCreatedV1;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.UserRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provisions a new Keycloak user, creates the local identity projection, publishes
 * {@code user.created.v1} through the transactional outbox, and writes an audit record. The mediator
 * TransactionBehavior wraps this command in a transaction so the JPA insert and outbox row commit
 * atomically (ADR-005, ADR-009).
 */
@Component
public class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, UserResponse> {

    private static final String AGGREGATE_TYPE = "User";
    private static final String OUTBOX_AGGREGATE_TYPE = "user";
    private static final String EVENT_TYPE = "user.created.v1";

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final OutboxService outboxService;
    private final AuditLogWriter auditLogWriter;

    public CreateUserCommandHandler(UserRepository userRepository,
                                    KeycloakAdminClient keycloakAdminClient,
                                    OutboxService outboxService,
                                    AuditLogWriter auditLogWriter) {
        this.userRepository = userRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.outboxService = outboxService;
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public UserResponse handle(CreateUserCommand command) {
        String keycloakId = keycloakAdminClient.createUser(command.username(), command.email());

        User user = User.provision(keycloakId, command.username(), command.email());
        user.activate();

        userRepository.save(user);

        String userId = user.getId().toString();

        outboxService.publish(
                OUTBOX_AGGREGATE_TYPE,
                userId,
                EVENT_TYPE,
                new UserCreatedV1(userId, user.getUsername(), user.getEmail(),
                        user.getCreatedAt().toString())
        );

        auditLogWriter.log("USER_CREATED", AGGREGATE_TYPE, userId,
                Map.of("username", command.username()));

        return UserResponse.from(user);
    }
}
