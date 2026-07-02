package com.telco.identity.application.command;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.Unit;

import java.util.UUID;

public record DeleteUserCommand(UUID userId) implements Command<Unit> {
}
