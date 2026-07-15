package com.telco.order.infrastructure.client;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.exception.DependencyFailureException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * HTTP client for campaign-service's internal validate endpoint. Uses Spring's {@link RestClient}
 * with a Resilience4j {@link CircuitBreaker} for fault tolerance, mirroring
 * {@link ProductCatalogServiceClient}'s structure.
 *
 * <p>Targets {@code POST /internal/campaigns/validate}, tokenless (network-perimeter trust,
 * ADR-027 Decision Section 4 second ratification addendum, tech-lead ruling 2026-07-13): campaign-
 * service's {@code CampaignSecurityConfig} permits {@code /internal/**} explicitly and the gateway
 * excludes {@code /internal/**} from public traffic - the same tokenless internal-call pattern
 * {@link ProductCatalogServiceClient} already establishes for {@code /internal/tariffs/**}.
 *
 * <p><b>Fail-open by design</b> (ADR-027 Decision Section 4: "if campaign-service is unreachable,
 * the order proceeds at the full undiscounted price"). This is the one deliberate behavioral
 * difference from {@link ProductCatalogServiceClient}, which is fail-closed (a missing/unreachable
 * tariff must block order creation - a tariff is mandatory data). Both {@link CallNotPermittedException}
 * (circuit OPEN) and any other call failure (network error, non-2xx, timeout - surfaced here as a
 * caught {@link DependencyFailureException} or any other {@link Exception}) are caught INSIDE this
 * client and mapped to {@link #NOT_ELIGIBLE_SENTINEL} rather than propagated. This is intentionally
 * NOT implemented as a try/catch around the call site in {@code CreateOrderCommandHandler}: keeping
 * the safety property encapsulated here means a future maintainer editing the handler cannot
 * accidentally regress it to fail-closed.
 */
@Component
public class CampaignServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CampaignServiceClient.class);
    private static final ParameterizedTypeReference<ApiResult<CampaignValidationResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    /**
     * Returned whenever campaign-service cannot be reached, responds unexpectedly, or its circuit
     * breaker is OPEN - the fail-open sentinel: not eligible, no discount, order proceeds at the full
     * undiscounted price.
     */
    public static final CampaignValidationResponse NOT_ELIGIBLE_SENTINEL =
            new CampaignValidationResponse(false, null, null, null, "CAMPAIGN_SERVICE_UNAVAILABLE");

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CampaignServiceClient(RestClient campaignRestClient, CircuitBreaker campaignServiceCircuitBreaker) {
        this.restClient = campaignRestClient;
        this.circuitBreaker = campaignServiceCircuitBreaker;
    }

    /**
     * Resolves the eligibility/discount decision for {@code customerId} against {@code tariffCode}
     * (and, if given, a specific {@code campaignCode}; otherwise campaign-service auto-resolves the
     * best-matching ACTIVE campaign for the tariff). Never throws: any failure to reach
     * campaign-service, an unexpected response, or an OPEN circuit breaker resolves to
     * {@link #NOT_ELIGIBLE_SENTINEL} so the caller always prices at the undiscounted rate rather than
     * being blocked (ADR-027 Decision Section 4).
     */
    public CampaignValidationResponse validate(UUID customerId, String tariffCode, String campaignCode) {
        try {
            return CircuitBreaker.decorateCallable(circuitBreaker, () -> {
                ApiResult<CampaignValidationResponse> result = restClient.post()
                        .uri("/internal/campaigns/validate")
                        .body(new ValidateRequestBody(customerId, tariffCode, campaignCode))
                        .retrieve()
                        .body(RESPONSE_TYPE);
                if (result == null || !result.success()) {
                    throw new DependencyFailureException(
                            "Unexpected response from campaign-service validating tariffCode: " + tariffCode, null);
                }
                return result.data();
            }).call();
        } catch (CallNotPermittedException e) {
            log.warn("campaign-service circuit breaker OPEN for tariffCode={}; proceeding without discount",
                    tariffCode);
            return NOT_ELIGIBLE_SENTINEL;
        } catch (DependencyFailureException e) {
            log.warn("campaign-service returned an unexpected response for tariffCode={}; "
                    + "proceeding without discount", tariffCode, e);
            return NOT_ELIGIBLE_SENTINEL;
        } catch (Exception e) {
            log.warn("Failed to call campaign-service for tariffCode={}; proceeding without discount",
                    tariffCode, e);
            return NOT_ELIGIBLE_SENTINEL;
        }
    }

    /** Request body for {@code POST /internal/campaigns/validate}. */
    private record ValidateRequestBody(UUID customerId, String tariffCode, String campaignCode) {
    }
}
