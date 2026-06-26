package com.telco.customer.application.command;

import com.telco.customer.application.dto.CustomerResponse;
import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Approves KYC (PENDING -> ACTIVE) and publishes customer.kyc-approved.v1 (FR-02, AC-01). */
public record ApproveKycCommand(UUID customerId) implements Command<CustomerResponse> {
}
