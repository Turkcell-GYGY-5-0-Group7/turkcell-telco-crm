package com.telco.acceptance.support;

import io.restassured.response.Response;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Provisions a real, freshly created Keycloak-backed SUBSCRIBER for the identity-to-customer
 * linkage flow (Section 14.1.1 ruling, Feature 14.4), replacing the permanently-unlinkable seeded
 * {@code subscriber@telco.local} (see {@link AcceptanceConfig#KEYCLOAK_SUBSCRIBER_USERNAME}) for
 * every test that needs to prove real "view my own resource" ownership rather than fall back to an
 * ADMIN token.
 *
 * <p>Each call creates a brand-new account via {@code POST /api/v1/users} (the only path that
 * creates the local identity-service {@code users} row the linkage consumer needs -
 * {@code LinkCustomerToUserCommandHandler} javadoc), with an initial password so it can
 * authenticate immediately (identity-service {@code CreateUserCommand}), then assigns it the
 * SUBSCRIBER realm role. The token obtained at this point carries no {@code customerId} claim yet
 * - only after the caller self-registers a customer (any {@code POST /api/v1/customers} call
 * authenticated with the token returned by {@link #provision(String)}) and the async
 * {@code customer.registered.v1} -&gt; identity-service inbox consumer completes the link does a
 * <em>fresh</em> token carry it, which is why {@link #awaitLinkedToken} always fetches an
 * uncached token and polls until the claim appears.
 */
public final class SelfServiceSubscriber {

    private final String username;
    private final String password;

    private SelfServiceSubscriber(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Creates a fresh SUBSCRIBER-role account and returns both its credentials and an initial
     * (pre-linkage) bearer token, suitable for self-registering a customer.
     */
    public static SelfServiceSubscriber provision(String adminToken) {
        String stamp = UUID.randomUUID().toString().replace("-", "");
        String username = "acc-subscriber-" + stamp + "@telco.local";
        String password = "Acc3ptance!" + stamp.substring(0, 12);

        Response created = GatewayApi.createUser(
                adminToken, username, username, "Acceptance", "Subscriber", password);
        created.then().statusCode(201);
        UUID userId = UUID.fromString(created.jsonPath().getString("data.id"));

        GatewayApi.assignRoles(adminToken, userId, List.of("SUBSCRIBER")).then().statusCode(200);

        return new SelfServiceSubscriber(username, password);
    }

    /** The initial bearer token, issued before any customer link exists ({@code customerId} claim is null). */
    public String initialToken() {
        return TokenProvider.freshTokenFor(username, password);
    }

    /**
     * Polls for a fresh token whose {@code customerId} claim matches the given customer (populated
     * asynchronously once identity-service's inbox consumer processes {@code customer.registered.v1}
     * - see {@code LinkCustomerToUserCommandHandler}). Never returns a cached/stale token, since the
     * claim is fixed at issuance time and the whole point is to observe it appear.
     */
    public String awaitLinkedToken(UUID expectedCustomerId) {
        AtomicReference<String> linked = new AtomicReference<>();
        await("identity-service links the self-registered customer and the next token carries customerId")
                .atMost(AcceptanceConfig.SAGA_TIMEOUT)
                .pollInterval(AcceptanceConfig.POLL_INTERVAL)
                .untilAsserted(() -> {
                    String token = TokenProvider.freshTokenFor(username, password);
                    assertThat(JwtClaims.customerId(token)).isEqualTo(expectedCustomerId.toString());
                    linked.set(token);
                });
        return linked.get();
    }
}
