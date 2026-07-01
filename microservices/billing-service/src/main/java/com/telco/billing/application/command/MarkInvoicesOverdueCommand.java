package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

/** Triggered by the overdue scheduler to mark unpaid invoices past due date as OVERDUE. */
public record MarkInvoicesOverdueCommand() implements Command<Integer> {}
