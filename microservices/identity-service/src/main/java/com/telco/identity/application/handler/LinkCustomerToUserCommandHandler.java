package com.telco.identity.application.handler;

import com.telco.identity.application.AuditLogWriter;
import com.telco.identity.application.command.LinkCustomerToUserCommand;
import com.telco.identity.domain.User;
import com.telco.identity.infrastructure.KeycloakAdminClient;
import com.telco.identity.infrastructure.persistence.UserRepository;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.cqrs.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Links the local identity projection to a customer-service aggregate id, closing the
 * identity-to-customer linkage gap for genuine self-service registrations (Section 14.1.1 ruling).
 *
 * <p>Deliberately tolerant of a missing {@code User} row (architecture-flagged provisioning-race check,
 * confirmed real): identity-service's ONLY path that creates a local {@code users} row today is the
 * explicit {@code POST /api/v1/users} admin flow ({@link CreateUserCommandHandler}), which mints a
 * brand-new Keycloak account and the local projection atomically. There is no just-in-time
 * provisioning on login - a Keycloak subject that already has an account through any other path (a
 * realm-seeded demo user, a Keycloak-native self-registration flow, or an account created directly in
 * the Keycloak admin console) has no corresponding {@code users} row and may never get one. So "no
 * matching user for this keycloakId" is an expected, permanently-possible outcome here, not merely a
 * narrow timing window - this handler treats it as a safe no-op, never an exception, never a retry
 * loop. The
 * {@link com.telco.identity.application.consumer.CustomerRegisteredEventConsumer} that dispatches this
 * command already guarantees {@code keycloakId} is only ever the {@code registeredByUserId} carried by
 * a genuine self-service registration event; agent/dealer-assisted registrations never reach this
 * handler at all.
 *
 * <p>Once the local link is persisted, the {@code customer_id} Keycloak user attribute is pushed via
 * {@link KeycloakAdminClient#setCustomerIdAttribute} so the {@code customerId} protocol mapper has
 * something to read on the user's next login (Feature 14.4). The local write is the source of truth
 * and is deliberately never rolled back over a Keycloak-side failure: the {@code keycloak-admin}
 * circuit breaker already isolates that call, and this handler additionally swallows any exception it
 * raises (rather than letting it propagate and unwind the mediator transaction) so a transient Admin
 * API hiccup never discards a legitimate local link. The attribute push is idempotent and safe to
 * reconcile/retry later - a repeat of the same {@code customer.registered.v1} event through the inbox
 * would simply re-attempt it.
 */
@Component
public class LinkCustomerToUserCommandHandler
        implements CommandHandler<LinkCustomerToUserCommand, Unit> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LinkCustomerToUserCommandHandler.class);
    private static final String AGGREGATE_TYPE = "User";

    private final UserRepository userRepository;
    private final AuditLogWriter auditLogWriter;
    private final KeycloakAdminClient keycloakAdminClient;

    public LinkCustomerToUserCommandHandler(UserRepository userRepository,
                                            AuditLogWriter auditLogWriter,
                                            KeycloakAdminClient keycloakAdminClient) {
        this.userRepository = userRepository;
        this.auditLogWriter = auditLogWriter;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    @Override
    public Unit handle(LinkCustomerToUserCommand command) {
        User user = userRepository.findByKeycloakId(command.keycloakId()).orElse(null);
        if (user == null) {
            LOGGER.info("No User projection for keycloakId={} yet - customerId={} stays unlinked "
                    + "until the user's projection is provisioned (safe no-op, not an error)",
                    command.keycloakId(), command.customerId());
            return Unit.INSTANCE;
        }

        user.linkCustomer(command.customerId());
        userRepository.save(user);

        auditLogWriter.log("USER_CUSTOMER_LINKED", AGGREGATE_TYPE, user.getId().toString(),
                Map.of("customerId", command.customerId().toString()));

        try {
            keycloakAdminClient.setCustomerIdAttribute(command.keycloakId(), command.customerId());
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to push customer_id attribute to Keycloak for keycloakId={}, "
                    + "customerId={} - local link is persisted and will not be rolled back; "
                    + "the JWT customerId claim will lag until this is reconciled/retried",
                    command.keycloakId(), command.customerId(), e);
        }

        return Unit.INSTANCE;
    }
}
