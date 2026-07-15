package com.telco.subscription.application.command;

/** Outcome of one reaper sweep: how many expired {@code RESERVED} holds were released to {@code FREE}. */
public record ExpireMsisdnReservationsResult(int releasedCount) {
}
