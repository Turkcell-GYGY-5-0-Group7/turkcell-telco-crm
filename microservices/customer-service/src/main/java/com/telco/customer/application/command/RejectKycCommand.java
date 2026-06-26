package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Rejects KYC (PENDING -> REJECTED) and publishes customer.kyc-rejected.v1 (FR-02). */
public record RejectKycCommand(
        UUID customerId,
        String reason
) implements Command<CustomerResponse> {
}
