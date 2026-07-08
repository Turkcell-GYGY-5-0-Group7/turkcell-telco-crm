package com.telco.identity.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Links the identity projection for the Keycloak subject {@code keycloakId} to the given
 * customer-service aggregate id. Dispatched by
 * {@link com.telco.identity.application.consumer.CustomerRegisteredEventConsumer} in response to a
 * self-service {@code customer.registered.v1} event (Section 14.1.1 ruling). A {@code keycloakId}
 * with no matching {@code users} row is a safe no-op, not an error (see the handler).
 */
public record LinkCustomerToUserCommand(
        @NotBlank String keycloakId,
        @NotNull UUID customerId
) implements Command<Unit> {
}
