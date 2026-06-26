package com.telco.customer.domain;

/**
 * KYC lifecycle state of a {@link Customer} (FR-02).
 *
 * <p>A customer is created {@link #PENDING}. After the KYC decision it becomes either {@link #ACTIVE}
 * (approved) or {@link #REJECTED}. These are terminal decisions: a customer cannot return to PENDING.
 */
public enum CustomerStatus {
    PENDING,
    ACTIVE,
    REJECTED
}
