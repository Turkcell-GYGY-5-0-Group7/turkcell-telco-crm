package com.telco.webbff.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Optional;

/**
 * Relays the caller's inbound {@code Authorization: Bearer <jwt>} header onto every outbound call
 * web-bff makes to the API gateway, so the gateway validates the same Keycloak token and forwards
 * the resolved identity ({@code X-User-Id}/{@code X-User-Roles}) downstream (ADR-011 Section 5,
 * ADR-022). The BFF only relays the token; it never mints, exchanges, or rewrites one.
 *
 * <p>An already-present {@code Authorization} header on the outbound request is left untouched, so
 * callers can override the relay explicitly when needed.
 */
@Component
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            currentAuthorizationHeader()
                    .ifPresent(token -> request.getHeaders().set(HttpHeaders.AUTHORIZATION, token));
        }
        return execution.execute(request, body);
    }

    private Optional<String> currentAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String header = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && !header.isBlank()) {
                return Optional.of(header);
            }
        }
        return Optional.empty();
    }
}
