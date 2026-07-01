package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;
import java.util.UUID;

public record RecordOverageCommand(
        UUID subscriptionId,
        Instant periodStart,
        Instant periodEnd,
        long voiceOverageSeconds,
        long smsOverageCount,
        long dataOverageKb,
        Instant aggregatedAt
) implements Command<Void> {}
