package com.telco.webbff.dto;

/**
 * UI-shaped usage roll-up for the account screen, composed from usage-service. Values are the
 * current billing period's consumption against the plan allowance.
 */
public record UsageSummary(
        long dataUsedMb,
        long dataAllowanceMb,
        long voiceUsedMinutes,
        long voiceAllowanceMinutes,
        long smsUsed,
        long smsAllowance) {
}
