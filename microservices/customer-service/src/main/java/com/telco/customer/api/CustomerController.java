package com.telco.customer.api;

import com.telco.customer.application.command.DeleteCustomerCommand;
import com.telco.customer.application.command.RegisterCustomerCommand;
import com.telco.customer.application.command.UpdateCustomerCommand;
import com.telco.customer.application.dto.CustomerResponse;
import com.telco.customer.application.dto.RegisterCustomerRequest;
import com.telco.customer.application.dto.UpdateCustomerRequest;
import com.telco.customer.application.query.GetCustomerQuery;
import com.telco.customer.application.query.ListCustomersQuery;
import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.context.UserContext;
import com.telco.platform.common.context.UserContextHolder;
import com.telco.platform.cqrs.Unit;
import com.telco.platform.mediator.Mediator;
import com.telco.platform.starter.api.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Customer management API. Thin edge: HTTP -> command/query via {@link Mediator} -> {@link ApiResult}
 * (ADR-004, ADR-015). No business logic here. All endpoints require a valid JWT.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final Mediator mediator;
    private final ApiResponseFactory responses;

    public CustomerController(Mediator mediator, ApiResponseFactory responses) {
        this.mediator = mediator;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUBSCRIBER', 'CALL_CENTER_AGENT', 'DEALER', 'ADMIN')")
    public ApiResult<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return responses.ok(mediator.send(new RegisterCustomerCommand(
                request.type(), request.firstName(), request.lastName(),
                request.identityNumber(), request.dateOfBirth(), resolveRegisteredByUserId())));
    }

    /**
     * Keycloak's own technical/composite roles that every user carries regardless of application
     * role assignment - never meaningful for the self-service-vs-staff-assisted distinction below.
     * {@code offline_access} and {@code uma_authorization} are the two client roles folded into the
     * {@code default-roles-<realm>} composite; the composite's own name embeds the realm, hence the
     * prefix match rather than an exact-name match (bug found and fixed during Feature 14.4
     * end-to-end verification - see the "Step 7" QA log in
     * {@code docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md}).
     */
    private static final Set<String> KEYCLOAK_TECHNICAL_ROLES = Set.of("offline_access", "uma_authorization");

    /**
     * Resolves the identity to attribute a registration to, for genuine self-service calls only.
     * A caller is self-service when their application-level roles (Keycloak's own technical/default
     * roles stripped out first) are exactly {@code {SUBSCRIBER}} - no
     * {@code CALL_CENTER_AGENT}/{@code DEALER}/{@code ADMIN} role present. Agent/dealer-assisted
     * registrations must not be attributed to the assisting staff member's own identity, so this
     * resolves to null for them (see the identity-to-customer linkage ruling in
     * {@code docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md}).
     * This is identity/channel resolution at the edge (who called, with which role), not domain
     * business logic - consistent with ADR-008, the same category as forwarding a correlationId.
     *
     * <p><b>Bug fixed during Feature 14.4 end-to-end verification:</b> the original check compared
     * the caller's raw roles set for exact equality against {@code {SUBSCRIBER}}. That works for a
     * realm-import-seeded demo user (Keycloak's bulk import applies only the {@code realmRoles}
     * explicitly listed in the export JSON), but Keycloak's Admin API - the only provisioning path
     * that creates a matching local {@code users} row in identity-service - always additionally
     * attaches the realm's {@code default-roles-<realm>} composite role (which itself expands to
     * {@code offline_access}/{@code uma_authorization}) to every user it creates. The exact-equality
     * check therefore misclassified every Admin-API-provisioned SUBSCRIBER as agent/dealer-assisted,
     * silently defeating self-service linkage for the only caller shape that can ever be linked -
     * confirmed live: {@code roles} claim {@code [default-roles-telco-crm, offline_access,
     * uma_authorization, SUBSCRIBER]} was rejected as non-self-service before this fix.
     */
    private String resolveRegisteredByUserId() {
        UserContext caller = UserContextHolder.get().orElse(UserContext.anonymous());
        Set<String> applicationRoles = caller.roles().stream()
                .filter(role -> !role.startsWith("default-roles-"))
                .filter(role -> !KEYCLOAK_TECHNICAL_ROLES.contains(role))
                .collect(java.util.stream.Collectors.toSet());
        boolean selfService = applicationRoles.equals(Set.of("SUBSCRIBER"));
        return selfService ? caller.userId() : null;
    }

    // Staff-only until the customerId-to-Keycloak-subject linkage lands (see
    // docs/tasks/sprint-14-testing-and-hardening/14.1.1-identity-linkage-gap-ruling.md); a
    // SUBSCRIBER caller cannot yet be verified as the owner of a given customer record.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<CustomerResponse> get(@PathVariable UUID id) {
        return responses.ok(mediator.query(new GetCustomerQuery(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<PageResult<CustomerResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.ok(mediator.query(new ListCustomersQuery(page, size)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_CENTER_AGENT')")
    public ApiResult<CustomerResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateCustomerRequest request) {
        return responses.ok(mediator.send(new UpdateCustomerCommand(
                id, request.firstName(), request.lastName(), request.dateOfBirth())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Unit> delete(@PathVariable UUID id) {
        return responses.ok(mediator.send(new DeleteCustomerCommand(id)));
    }
}
