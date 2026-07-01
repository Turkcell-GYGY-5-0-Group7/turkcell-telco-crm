package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

public record RecordSubscriptionSuspendedCommand(
        UUID subscriptionId,
        Instant suspendedAt
) implements Command<Void> {}
