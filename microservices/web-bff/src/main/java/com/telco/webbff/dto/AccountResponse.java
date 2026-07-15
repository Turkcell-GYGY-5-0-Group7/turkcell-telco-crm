package com.telco.webbff.dto;

import java.util.List;

/**
 * Account composition for {@code GET /bff/v1/account}: the caller's profile plus every subscription
 * with its per-subscription usage/quota (web-bff contract). Usage is attached per subscription rather
 * than as a single roll-up so the UI can render a gauge for each active line. UI DTO, not
 * {@code ApiResult<T>} (ADR-015 BFF exception). Scoped strictly to the authenticated caller.
 */
public record AccountResponse(
        ProfileSummary profile,
        List<AccountSubscription> subscriptions) {
}
