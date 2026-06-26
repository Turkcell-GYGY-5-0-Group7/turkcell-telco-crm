package com.telco.customer.application.dto;

/** KYC rejection input (FR-02). The reason is optional and is recorded on the rejection event/audit. */
public record RejectKycRequest(
        String reason
) {
}
