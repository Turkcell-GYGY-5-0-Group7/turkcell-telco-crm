package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

public record RecordSubscriptionTerminatedCommand(
        UUID subscriptionId,
        Instant terminatedAt
) implements Command<Void> {}
