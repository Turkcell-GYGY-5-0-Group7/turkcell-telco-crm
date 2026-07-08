package com.telco.identity.application.command;

import com.telco.identity.application.dto.UserResponse;
import com.telco.platform.cqrs.Command;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Provisions a new user in Keycloak and creates the local identity projection (FR-IAM-03).
 *
 * <p>{@code firstName}/{@code lastName} are required (not merely cosmetic): the realm's declarative
 * Keycloak User Profile marks {@code firstName}, {@code lastName}, and {@code email} as required for
 * the account holder's own ("user") context. A user created without them fails Keycloak's
 * {@code VERIFY_PROFILE} required-action trigger evaluation on every subsequent login attempt (added
 * automatically, invisibly - it does not show up as a value read back off {@code requiredActions}
 * until it has already blocked a login), and the Resource Owner Password Credentials grant can never
 * satisfy an interactive required action, so the account is permanently locked out with
 * {@code invalid_grant}/{@code resolve_required_actions} ("Account is not fully set up") the moment it
 * tries to authenticate. This was a real, previously-undiscovered defect (Feature 14.4 end-to-end
 * verification): confirmed live that patching a stuck account's {@code firstName}/{@code lastName}
 * (and {@code emailVerified}) immediately un-blocks its next login attempt with no other change.
 *
 * <p>{@code password} is optional (nullable) and, when present, sets a non-temporary initial
 * credential on the Keycloak user (via a dedicated reset-password call, not embedded in the create
 * body - see {@code KeycloakAdminRestClient}). Callers that omit it get the pre-existing behavior: a
 * user with no credential at all, expected to have one established through a separate out-of-band
 * flow. Self-service/test callers that need the created account to authenticate immediately (this
 * feature's identity-to-customer linkage proof requires a real subscriber to log in and self-register
 * a customer) must supply one.
 */
public record CreateUserCommand(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @Size(min = 8, max = 72) String password
) implements Command<UserResponse> {
}
