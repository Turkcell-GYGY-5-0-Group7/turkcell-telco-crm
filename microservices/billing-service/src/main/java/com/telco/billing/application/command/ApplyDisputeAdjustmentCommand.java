package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reacts to {@code dispute.resolved-customer.v1} on an unpaid invoice - applies a real credit
 * adjustment (ADR-028 Section 5). No-op if the invoice is already {@code PAID} (payment-service's
 * refund path owns that case) or if the hold is not currently set.
 */
public record ApplyDisputeAdjustmentCommand(UUID invoiceId, BigDecimal resolutionAmount) implements Command<Void> {}
