package com.telco.webbff.client;

import com.telco.platform.common.exception.AccessDeniedException;
import com.telco.platform.common.exception.BusinessRuleException;
import com.telco.platform.common.exception.ConflictException;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ResourceNotFoundException;
import com.telco.platform.common.exception.UnauthenticatedException;
import com.telco.platform.common.exception.ValidationException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Thin, reusable transport over the gateway {@link RestClient}. The BFF composition endpoints
 * (Sprint 16 features 16.4 and 16.5) build on this single client; the caller's bearer token is
 * relayed automatically by the configured interceptor. web-bff targets only gateway routes under
 * {@code /api/v1/**} (ADR-022, ADR-011 Section 2) - never a domain-service host or port.
 *
 * <p>Downstream failures are translated into platform exceptions here so the BFF never leaks an
 * unhandled 500: a gateway {@code 4xx} maps to the matching client status (404/409/422/...) and a
 * {@code 5xx} or connection failure maps to {@code DependencyFailureException} (503). The
 * {@code starter-api} {@code GlobalExceptionHandler} renders the final {@code ApiResult} error body.
 */
@Component
public class GatewayClient {

    /** Header carrying the idempotency key on order/payment writes (mandatory downstream). */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient gatewayRestClient;

    public GatewayClient(RestClient gatewayRestClient) {
        this.gatewayRestClient = gatewayRestClient;
    }

    /** GET a gateway route (relative to the configured gateway base URL) into a simple type. */
    public <T> T get(String uri, Class<T> responseType) {
        return execute(uri, () -> gatewayRestClient.get().uri(uri).retrieve().body(responseType));
    }

    /** GET a gateway route into a generic/parameterized type (e.g. {@code ApiResult<List<T>>}). */
    public <T> T get(String uri, ParameterizedTypeReference<T> responseType) {
        return execute(uri, () -> gatewayRestClient.get().uri(uri).retrieve().body(responseType));
    }

    /** POST a JSON body to a gateway route into a generic/parameterized type. */
    public <T> T post(String uri, Object body, ParameterizedTypeReference<T> responseType) {
        return execute(uri, () -> gatewayRestClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType));
    }

    /**
     * POST a JSON body to a gateway route, relaying the caller's {@code Idempotency-Key} downstream
     * so the owning domain service can enforce idempotency (a replay with the same key returns the
     * original result). web-bff only forwards the key; it does not generate or enforce idempotency.
     */
    public <T> T post(String uri, Object body, String idempotencyKey,
                      ParameterizedTypeReference<T> responseType) {
        return execute(uri, () -> gatewayRestClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .body(body)
                .retrieve()
                .body(responseType));
    }

    /** POST a multipart body (e.g. a KYC document upload) to a gateway route. */
    public <T> T postMultipart(String uri, MultiValueMap<String, Object> parts,
                               ParameterizedTypeReference<T> responseType) {
        return execute(uri, () -> gatewayRestClient.post().uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(responseType));
    }

    private <T> T execute(String uri, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw translate(uri, ex);
        } catch (ResourceAccessException ex) {
            throw new DependencyFailureException("gateway is unreachable for " + uri, ex);
        }
    }

    private RuntimeException translate(String uri, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String message = "gateway call to " + uri + " failed with status " + status;
        return switch (status) {
            case 400 -> new ValidationException(message, Map.of());
            case 401 -> new UnauthenticatedException(message);
            case 403 -> new AccessDeniedException(message);
            case 404 -> new ResourceNotFoundException(message);
            case 409 -> new ConflictException(message);
            // 413 PAYLOAD_TOO_LARGE is a CLIENT error - the caller sent more bytes than the route
            // accepts (e.g. an oversize KYC document on the multipart upload). Falling through to
            // the default arm below would report it as a 503 DEPENDENCY_FAILURE ("the platform is
            // broken"), which is a lie the UI cannot act on. Surfaced as 400 VALIDATION_FAILED so
            // the browser gets a 4xx with a message it can show. (Note: customer-service today
            // renders a multipart overflow as 500 - see PAYLOAD_TOO_LARGE handling in
            // OnboardingCompositionService, which rejects an oversize document BEFORE it is
            // dispatched. This arm covers any hop that answers 413 honestly: the gateway, a
            // reverse proxy, or customer-service once its multipart advice is fixed.)
            case 413 -> new ValidationException(
                    message + " (payload too large - the uploaded document exceeds the accepted size)",
                    Map.of("payload", "too large"));
            case 422 -> new BusinessRuleException(message);
            default -> new DependencyFailureException(message, ex);
        };
    }
}
