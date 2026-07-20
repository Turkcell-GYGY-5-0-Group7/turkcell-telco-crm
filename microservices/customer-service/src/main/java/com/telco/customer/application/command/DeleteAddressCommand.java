package com.telco.customer.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;

import java.util.UUID;

/** Deletes an address that belongs to the given customer (FR-03). */
public record DeleteAddressCommand(
        UUID customerId,
        UUID addressId
) implements Command<Unit> {
}
