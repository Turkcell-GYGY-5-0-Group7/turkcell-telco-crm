package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

public record MarkInvoicePaidCommand(UUID invoiceId) implements Command<Void> {}
