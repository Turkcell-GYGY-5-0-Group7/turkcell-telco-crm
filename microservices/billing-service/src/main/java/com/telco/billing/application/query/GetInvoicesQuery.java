package com.telco.billing.application.query;

import com.telco.billing.application.dto.InvoiceResponse;
import com.telco.platform.common.api.PageResult;
import com.telco.platform.cqrs.Query;

import java.util.UUID;

public record GetInvoicesQuery(UUID customerId, int page, int size,
                               String callerUserId, boolean callerIsAdmin)
        implements Query<PageResult<InvoiceResponse>> {}
