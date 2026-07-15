package com.telco.webbff.controller;

import com.telco.webbff.dto.AccountResponse;
import com.telco.webbff.dto.HomeResponse;
import com.telco.webbff.dto.InvoicesResponse;
import com.telco.webbff.dto.OnboardingCatalogResponse;
import com.telco.webbff.dto.OnboardingOrderRequest;
import com.telco.webbff.dto.OnboardingOrderResponse;
import com.telco.webbff.service.AccountCompositionService;
import com.telco.webbff.service.OnboardingCompositionService;
import com.telco.platform.common.exception.ValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web BFF composition surface (web-bff contract, ADR-022). Exposes the five UI-shaped composition
 * endpoints under {@code /bff/v1} for the SvelteKit web app. Every route requires a valid JWT: none
 * is listed in {@code WebBffSecurityConfig}'s permit set, so all fall under {@code .anyRequest()
 * .authenticated()} and delegate authentication to {@code starter-security}.
 *
 * <p>Responses are UI DTOs returned directly, NOT wrapped in {@code ApiResult<T>}: the BFF is the
 * documented ADR-015 exception (docs/api-contracts/web-bff.md Notes). This is a Simple Service Layer
 * (ARC-02): the two onboarding endpoints ({@code /onboarding/catalog}, {@code /onboarding/order})
 * compose real gateway routes via {@link OnboardingCompositionService} (16.4.1); the remaining three
 * ({@code /home}, {@code /account}, {@code /invoices}) compose the caller's profile, subscriptions,
 * usage and invoices via {@link AccountCompositionService} (16.5.1). Every read is scoped strictly to
 * the authenticated caller's own identity; no client-supplied id widens scope. No domain logic or
 * persistence lives here (ADR-022).
 */
@RestController
@RequestMapping("/bff/v1")
@Tag(name = "Web BFF", description = "UI-shaped composition endpoints for the SvelteKit web channel")
public class BffController {

    private final OnboardingCompositionService onboarding;
    private final AccountCompositionService account;

    public BffController(OnboardingCompositionService onboarding, AccountCompositionService account) {
        this.onboarding = onboarding;
        this.account = account;
    }

    @Operation(summary = "Dashboard: profile, active subscriptions, and latest invoice, composed.")
    @GetMapping("/home")
    public HomeResponse home() {
        // Single BFF call; the profile + active-subscriptions + latest-invoice fan-out happens
        // server-side, scoped to the caller's own resolved customerId (16.5.1).
        return account.home();
    }

    @Operation(summary = "Tariffs and addons shaped for the onboarding wizard.")
    @GetMapping("/onboarding/catalog")
    public OnboardingCatalogResponse onboardingCatalog() {
        // Single BFF call; the tariffs + per-tariff addons fan-out happens server-side (16.4.1).
        return onboarding.catalog();
    }

    @Operation(summary = "Orchestrates customer check and order placement for the onboarding wizard.")
    @PostMapping("/onboarding/order")
    public OnboardingOrderResponse placeOnboardingOrder(
            @Parameter(description = "Mandatory idempotency key for the order write (web-bff contract).",
                    required = true)
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OnboardingOrderRequest request) {
        // The Idempotency-Key is mandatory on order/payment writes (web-bff contract). Validated here
        // so a missing key maps to a 400 via the platform handler rather than a generic 500 from the
        // container's MissingRequestHeaderException. The key is relayed unchanged to order-service,
        // which enforces idempotency (a replay with the same key returns the original order).
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required",
                    Map.of("Idempotency-Key", "must not be blank"));
        }
        return onboarding.placeOrder(idempotencyKey, request);
    }

    @Operation(summary = "Profile and subscriptions with per-subscription usage/quota, composed.")
    @GetMapping("/account")
    public AccountResponse account() {
        // Single BFF call; the profile + subscriptions + per-active-subscription usage fan-out happens
        // server-side, scoped to the caller's own resolved customerId (16.5.1).
        return account.account();
    }

    @Operation(summary = "Paged invoice list, each entry carrying a usable PDF-download link.")
    @GetMapping("/invoices")
    public InvoicesResponse invoices(
            @Parameter(description = "Zero-based page index.")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size.")
            @RequestParam(defaultValue = "20") int size) {
        // page/size are the only inputs; the customerId is the caller's own resolved id, never taken
        // from the request, so a client cannot list another customer's invoices (16.5.1).
        return account.invoices(page, size);
    }
}
