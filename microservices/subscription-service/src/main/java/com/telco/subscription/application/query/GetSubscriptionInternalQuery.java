package com.telco.subscription.application.query;

import com.telco.platform.cqrs.Query;
import com.telco.subscription.application.dto.SubscriptionInternalResponse;

import java.util.UUID;

/**
 * System read of a single subscription by id for the trusted internal endpoint
 * ({@code GET /internal/subscriptions/{id}}, Sprint 24 Feature 24.2). Distinct from the guarded
 * {@link GetSubscriptionQuery}: it carries NO caller identity and its handler enforces NO ownership
 * guard - the gateway blocks {@code /internal/**} from external traffic (network-perimeter trust).
 * A missing id raises {@code ResourceNotFoundException} (-&gt; 404).
 */
public record GetSubscriptionInternalQuery(
        UUID subscriptionId
) implements Query<SubscriptionInternalResponse> {
}
