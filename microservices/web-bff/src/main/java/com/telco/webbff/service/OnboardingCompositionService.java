package com.telco.webbff.service;

import com.telco.platform.common.api.ApiResult;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.common.exception.DependencyFailureException;
import com.telco.platform.common.exception.ValidationException;
import com.telco.webbff.client.GatewayClient;
import com.telco.webbff.dto.AddonOption;
import com.telco.webbff.dto.CustomerRegistration;
import com.telco.webbff.dto.KycDocument;
import com.telco.webbff.dto.OnboardingCatalogResponse;
import com.telco.webbff.dto.OnboardingOrderRequest;
import com.telco.webbff.dto.OnboardingOrderResponse;
import com.telco.webbff.dto.TariffOption;
import com.telco.webbff.gateway.GatewayAddon;
import com.telco.webbff.gateway.GatewayCustomer;
import com.telco.webbff.gateway.GatewayDocument;
import com.telco.webbff.gateway.GatewayOrder;
import com.telco.webbff.gateway.GatewayTariff;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Onboarding composition (web-bff contract, ADR-022; feature 16.4.1). This is the Simple Service
 * Layer body behind the two onboarding endpoints: it fans out to gateway routes ({@code /api/v1/**})
 * and shapes the result into UI DTOs. It holds no state and owns no data - every read/write goes to
 * the owning domain service through the single {@link GatewayClient}; the caller's bearer token is
 * relayed automatically. No domain logic lives here beyond request orchestration and shaping.
 */
@Service
public class OnboardingCompositionService {

    private static final ParameterizedTypeReference<ApiResult<PageResult<GatewayTariff>>> TARIFF_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<PageResult<GatewayAddon>>> ADDON_PAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<GatewayTariff>> TARIFF =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<GatewayCustomer>> CUSTOMER =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<GatewayDocument>> DOCUMENT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResult<GatewayOrder>> ORDER =
            new ParameterizedTypeReference<>() { };

    /** Upper bound for the onboarding catalog fan-out; the catalog is a small, curated set. */
    private static final int CATALOG_PAGE_SIZE = 100;

    private final GatewayClient gateway;

    public OnboardingCompositionService(GatewayClient gateway) {
        this.gateway = gateway;
    }

    /**
     * Composes the onboarding catalog: one call fetches the active tariffs, then their addons are
     * fetched per tariff and tagged with the owning tariff code. The browser makes a single BFF call;
     * the fan-out happens server-side so no client-side composition is required.
     */
    public OnboardingCatalogResponse catalog() {
        List<GatewayTariff> tariffs = page(gateway.get(
                "/api/v1/tariffs?page=0&size=" + CATALOG_PAGE_SIZE, TARIFF_PAGE));

        List<TariffOption> tariffOptions = new ArrayList<>();
        List<AddonOption> addonOptions = new ArrayList<>();
        for (GatewayTariff tariff : tariffs) {
            tariffOptions.add(new TariffOption(
                    tariff.code(), tariff.name(), tariff.targetSegment(),
                    tariff.monthlyFee(), tariff.currency()));

            List<GatewayAddon> addons = page(gateway.get(
                    "/api/v1/addons?tariffCode=" + tariff.code() + "&page=0&size=" + CATALOG_PAGE_SIZE,
                    ADDON_PAGE));
            for (GatewayAddon addon : addons) {
                addonOptions.add(new AddonOption(
                        addon.code(), addon.name(), tariff.code(), addon.price(), addon.currency()));
            }
        }
        return new OnboardingCatalogResponse(tariffOptions, addonOptions);
    }

    /**
     * Orchestrates the onboarding order: register (or reuse) the customer, upload the KYC document for
     * a newly registered customer, resolve the selected tariff code to its id, and place the order via
     * the gateway carrying the mandatory {@code Idempotency-Key}. Returns the order id and saga status
     * so the UI can poll {@code GET /api/v1/orders/{id}}. Idempotency is enforced by order-service; the
     * BFF only forwards the key, so a replay with the same key returns the original order downstream.
     */
    public OnboardingOrderResponse placeOrder(String idempotencyKey, OnboardingOrderRequest request) {
        String customerId = resolveCustomer(request);
        UUID tariffId = resolveTariffId(request.tariffCode());

        Map<String, Object> orderBody = Map.of(
                "customerId", customerId,
                "items", List.of(Map.of("tariffId", tariffId.toString(), "quantity", 1)));
        GatewayOrder order = data(gateway.post("/api/v1/orders", orderBody, idempotencyKey, ORDER));

        return new OnboardingOrderResponse(order.id().toString(), order.status(), idempotencyKey);
    }

    /**
     * Returns the customer id to place the order against: the supplied one when reusing an existing
     * customer, or a freshly registered customer's id. Registration additionally uploads the KYC
     * document. The register-vs-reuse contract is validated here (400 on a missing block) rather than
     * surfacing downstream as a 500.
     */
    private String resolveCustomer(OnboardingOrderRequest request) {
        if (request.customerId() != null && !request.customerId().isBlank()) {
            return request.customerId();
        }

        CustomerRegistration registration = request.customer();
        if (registration == null) {
            throw new ValidationException(
                    "customer registration details are required when no customerId is supplied",
                    Map.of("customer", "must not be null"));
        }
        if (request.kycDocument() == null) {
            throw new ValidationException(
                    "a KYC document is required to register a new customer",
                    Map.of("kycDocument", "must not be null"));
        }

        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("type", registration.type());
        registerBody.put("firstName", registration.firstName());
        registerBody.put("lastName", registration.lastName());
        registerBody.put("identityNumber", registration.identityNumber());
        registerBody.put("dateOfBirth", registration.dateOfBirth());

        GatewayCustomer customer = data(gateway.post("/api/v1/customers", registerBody, CUSTOMER));
        uploadKyc(customer.id(), request.kycDocument());
        return customer.id().toString();
    }

    private void uploadKyc(UUID customerId, KycDocument document) {
        byte[] content = decode(document);
        String fileName = document.fileName() != null && !document.fileName().isBlank()
                ? document.fileName() : "kyc-document";

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("type", document.type());
        parts.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        gateway.postMultipart("/api/v1/customers/" + customerId + "/documents", parts, DOCUMENT);
    }

    private UUID resolveTariffId(String tariffCode) {
        GatewayTariff tariff = data(gateway.get("/api/v1/tariffs/" + tariffCode, TARIFF));
        if (tariff.id() == null) {
            throw new DependencyFailureException(
                    "tariff " + tariffCode + " was returned without an id", null);
        }
        return tariff.id();
    }

    private static byte[] decode(KycDocument document) {
        if (document.content() == null || document.content().isBlank()) {
            throw new ValidationException("KYC document content is required",
                    Map.of("kycDocument.content", "must not be blank"));
        }
        try {
            return Base64.getDecoder().decode(document.content());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("KYC document content must be valid Base64",
                    Map.of("kycDocument.content", "must be valid Base64"));
        }
    }

    private static <T> T data(ApiResult<T> result) {
        if (result == null || !result.success() || result.data() == null) {
            throw new DependencyFailureException("gateway returned an unsuccessful response", null);
        }
        return result.data();
    }

    private static <T> List<T> page(ApiResult<PageResult<T>> result) {
        PageResult<T> pageResult = data(result);
        return pageResult.content() == null ? List.of() : pageResult.content();
    }
}
