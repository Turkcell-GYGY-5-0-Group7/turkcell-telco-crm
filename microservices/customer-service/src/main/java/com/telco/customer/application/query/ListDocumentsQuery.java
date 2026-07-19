package com.telco.customer.application.query;

import com.telco.customer.application.dto.DocumentResponse;
import com.telco.platform.cqrs.Query;

import java.util.List;
import java.util.UUID;

/** Lists all KYC document metadata for a customer (FR-03). Binaries stay in MinIO. */
public record ListDocumentsQuery(UUID customerId) implements Query<List<DocumentResponse>> {
}
