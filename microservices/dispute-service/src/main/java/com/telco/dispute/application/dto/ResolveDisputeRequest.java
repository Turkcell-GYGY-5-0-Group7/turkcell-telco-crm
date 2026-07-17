package com.telco.dispute.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * {@code outcome} selects which of the two resolution commands the controller dispatches:
 * {@code CUSTOMER} (requires {@code resolutionAmount}) or {@code MERCHANT} (resolutionAmount ignored -
 * ADR-028 Section 5: no financial change).
 */
public record ResolveDisputeRequest(

        @NotNull
        Outcome outcome,

        @Positive
        BigDecimal resolutionAmount

) {
    public enum Outcome {
        CUSTOMER,
        MERCHANT
    }
}
