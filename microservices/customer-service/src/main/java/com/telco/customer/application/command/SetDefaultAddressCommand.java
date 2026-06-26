package com.telco.customer.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;

import java.util.UUID;

/** Makes one address the default, clearing the previous default (FR-03). */
public record SetDefaultAddressCommand(
        UUID customerId,
        UUID addressId
) implements Command<Unit> {
}
