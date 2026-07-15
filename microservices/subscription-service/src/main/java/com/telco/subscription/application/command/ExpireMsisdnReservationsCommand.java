package com.telco.subscription.application.command;

import com.telco.platform.cqrs.Command;

/**
 * Sweeps expired {@code RESERVED} MSISDN holds back to {@code FREE} (feature 17.3, FR-13, ADR-024).
 * Dispatched by {@link com.telco.subscription.infrastructure.scheduler.MsisdnReservationExpiryReaper}
 * under a {@code DistributedLock} so exactly one replica performs a given tick's sweep.
 */
public record ExpireMsisdnReservationsCommand() implements Command<ExpireMsisdnReservationsResult> {
}
