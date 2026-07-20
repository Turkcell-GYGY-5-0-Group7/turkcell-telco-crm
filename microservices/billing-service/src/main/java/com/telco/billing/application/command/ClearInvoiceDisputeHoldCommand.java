package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Reacts to {@code dispute.resolved-merchant.v1} - clears the hold, no financial change. */
public record ClearInvoiceDisputeHoldCommand(UUID invoiceId) implements Command<Void> {}
