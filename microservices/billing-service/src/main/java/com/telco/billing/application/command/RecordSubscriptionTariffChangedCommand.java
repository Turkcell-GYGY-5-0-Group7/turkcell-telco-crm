package com.telco.billing.application.command;

import com.telco.platform.cqrs.Command;

import java.util.UUID;

/** Applies a plan change to the billing record (FR-09): next bill-run prices the new tariff. */
public record RecordSubscriptionTariffChangedCommand(
        UUID subscriptionId,
        String newTariffCode
) implements Command<Void> {}
