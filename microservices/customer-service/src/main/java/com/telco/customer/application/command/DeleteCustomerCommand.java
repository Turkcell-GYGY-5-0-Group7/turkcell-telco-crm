package com.telco.customer.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;

import java.util.UUID;

/** Soft-deletes a customer, setting deleted_at (FR-04). */
public record DeleteCustomerCommand(UUID id) implements Command<Unit> {
}
