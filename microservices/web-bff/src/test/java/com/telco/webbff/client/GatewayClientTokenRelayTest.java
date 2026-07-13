package com.telco.webbff.client;

import com.telco.webbff.config.BearerTokenRelayInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Proves 16.1.1 acceptance criterion 2: a call through the client to a gateway route carries the
 * caller's bearer token on the outbound request. Uses {@link MockRestServiceServer} bound to the same
 * {@link RestClient.Builder} the {@link BearerTokenRelayInterceptor} is registered on, so the full
 * interceptor -> outbound-request path is exercised without a live gateway.
 */
class GatewayClientTokenRelayTest {

    private static final String GATEWAY = "http://gateway.test";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void relays_caller_bearer_token_on_outbound_gateway_call() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GATEWAY)
                .requestInterceptor(new BearerTokenRelayInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayClient client = new GatewayClient(builder.build());

        server.expect(requestTo(GATEWAY + "/api/v1/tariffs"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer relayed-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.AUTHORIZATION, "Bearer relayed-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        String body = client.get("/api/v1/tariffs", String.class);

        assertThat(body).isEqualTo("[]");
        server.verify();
    }

    @Test
    void makes_call_without_authorization_when_no_inbound_token() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GATEWAY)
                .requestInterceptor(new BearerTokenRelayInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayClient client = new GatewayClient(builder.build());

        server.expect(requestTo(GATEWAY + "/api/v1/tariffs"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // No request context set: nothing to relay, and the call must still succeed.
        String body = client.get("/api/v1/tariffs", String.class);

        assertThat(body).isEqualTo("[]");
        server.verify();
    }
}
