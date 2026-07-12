package com.telco.billing.application.command;

/**
 * Outcome of a bill-run invocation. {@code runAlreadyOwned} distinguishes the case where another
 * billing-service pod already owns this billing period's run (feature 17.4, ADR-024) from a normal
 * zero-work result (e.g. no active subscribers) - the losing pod's invocation never partitions or
 * dispatches any batches.
 */
public record RunBillResult(int invoicesGenerated, int invoicesSkipped, boolean runAlreadyOwned) {

    /** Existing 2-arg shape, preserved for callers that predate feature 17.4. */
    public RunBillResult(int invoicesGenerated, int invoicesSkipped) {
        this(invoicesGenerated, invoicesSkipped, false);
    }

    public static RunBillResult alreadyOwnedByAnotherPod() {
        return new RunBillResult(0, 0, true);
    }
}
