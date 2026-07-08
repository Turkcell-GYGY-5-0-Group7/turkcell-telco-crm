package com.telco.billing.application.query;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

/**
 * @param callerUserId     raw JWT subject; retained for audit/logging only, no longer used for the
 *                         ownership check (identity-to-customer linkage, ADR-011)
 * @param callerCustomerId resolved {@code customerId} claim linked to the caller's identity; null
 *                         when the caller is staff or the identity is not yet linked
 */
public record GetInvoiceByIdQuery(UUID invoiceId, String callerUserId, boolean callerIsAdmin,
                                  String callerCustomerId)
        implements Query<InvoiceResponse> {}
