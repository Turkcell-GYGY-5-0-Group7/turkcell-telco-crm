// Per-subscription usage/quota computation for the /account view (16.5.2).
//
// Pure and framework-agnostic (unit tested in Node). The BFF sends a UsageSummary
// per ACTIVE subscription (null otherwise); the page renders one gauge per metric.
// Percentages are clamped to 0-100 so a gauge never overflows its track, and a
// non-positive/absent allowance yields 0% rather than NaN/Infinity, so the UI
// never renders a broken bar.

import type { UsageSummary } from '$lib/api/client';

/** One usage dimension (data / voice / SMS) shaped for a gauge. */
export interface UsageMetric {
	/** Display label, e.g. "Data". */
	label: string;
	/** Consumed amount this billing period. */
	used: number;
	/** Plan allowance for the period. */
	allowance: number;
	/** Unit suffix, e.g. "MB", "min", "SMS". */
	unit: string;
}

/**
 * Consumed share of the allowance as a percentage, clamped to [0, 100]. A
 * non-finite or non-positive allowance (no quota) returns 0, so callers can render
 * a gauge without guarding against divide-by-zero.
 */
export function usagePercent(used: number, allowance: number): number {
	if (!Number.isFinite(allowance) || allowance <= 0) {
		return 0;
	}
	const pct = (used / allowance) * 100;
	if (pct <= 0) {
		return 0;
	}
	return pct > 100 ? 100 : pct;
}

/** Break a UsageSummary into the three gauge metrics, in display order. */
export function usageMetrics(usage: UsageSummary): UsageMetric[] {
	return [
		{ label: 'Data', used: usage.dataUsedMb, allowance: usage.dataAllowanceMb, unit: 'MB' },
		{
			label: 'Voice',
			used: usage.voiceUsedMinutes,
			allowance: usage.voiceAllowanceMinutes,
			unit: 'min'
		},
		{ label: 'SMS', used: usage.smsUsed, allowance: usage.smsAllowance, unit: 'SMS' }
	];
}

/** Human-readable "used / allowance unit" label, thousands-grouped. */
export function formatUsageLabel(metric: UsageMetric): string {
	const format = (value: number) => new Intl.NumberFormat('en').format(value);
	return `${format(metric.used)} / ${format(metric.allowance)} ${metric.unit}`;
}
