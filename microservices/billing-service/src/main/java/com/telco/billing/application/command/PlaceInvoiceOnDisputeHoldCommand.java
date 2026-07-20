package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Reacts to {@code dispute.opened.v1} - sets {@code Invoice.disputeStatus = ON_HOLD}, nothing else. */
public record PlaceInvoiceOnDisputeHoldCommand(UUID invoiceId) implements Command<Void> {}
