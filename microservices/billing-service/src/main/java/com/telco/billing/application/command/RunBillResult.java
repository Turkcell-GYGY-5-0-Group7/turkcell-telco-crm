package com.telco.billing.application.command;

public record RunBillResult(int invoicesGenerated, int invoicesSkipped) {}
