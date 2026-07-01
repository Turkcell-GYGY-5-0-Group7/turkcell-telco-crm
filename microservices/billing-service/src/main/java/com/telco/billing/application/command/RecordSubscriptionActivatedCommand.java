package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

public record RecordSubscriptionActivatedCommand(
        UUID subscriptionId,
        UUID customerId,
        String tariffCode,
        Instant activatedAt
) implements Command<Void> {}
