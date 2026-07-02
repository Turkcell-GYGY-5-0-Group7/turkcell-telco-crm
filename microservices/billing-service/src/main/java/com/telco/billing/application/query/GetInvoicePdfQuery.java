package com.telco.billing.application.query;

import com.telco.platform.cqrs.Query;

import java.util.UUID;

public record GetInvoicePdfQuery(UUID invoiceId, String callerUserId, boolean callerIsAdmin)
        implements Query<byte[]> {}
