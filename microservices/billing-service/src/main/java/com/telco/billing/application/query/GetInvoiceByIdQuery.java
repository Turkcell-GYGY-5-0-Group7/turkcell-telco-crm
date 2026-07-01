package com.telco.billing.application.query;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

public record GetInvoiceByIdQuery(UUID invoiceId, String callerUserId, boolean callerIsAdmin)
        implements Query<InvoiceResponse> {}
