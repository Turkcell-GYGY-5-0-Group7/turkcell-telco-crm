package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.time.Instant;

/** Admin-triggered or scheduled monthly bill-run for a given billing period. */
public record RunBillCommand(
        Instant periodStart,
        Instant periodEnd
) implements Command<RunBillResult> {}
