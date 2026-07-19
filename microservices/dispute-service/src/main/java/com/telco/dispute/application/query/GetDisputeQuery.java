package com.telco.dispute.application.query;

import com.telco.dispute.application.dto.DisputeResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * Fetches a single dispute by id. A non-admin caller may only read a dispute they own -
 * {@link com.telco.dispute.application.handler.GetDisputeQueryHandler} enforces this by comparing
 * {@code callerCustomerId} (the caller's own linked customer-service id, from
 * {@code UserContext.customerId()} - NOT the raw Keycloak subject) against the loaded
 * {@code Dispute.customerId}.
 */
public record GetDisputeQuery(
        UUID disputeId,
        String callerCustomerId,
        boolean callerIsAdmin
) implements Query<DisputeResponse> {
}
