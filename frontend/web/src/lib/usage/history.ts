// Pure helpers for the usage page (unit tested in Node).
//
// usage-service returns raw CDRs (one UsageHistoryItem per call/message/session);
// the page needs a default time window, a human unit per usage type, and a per-day
// roll-up to feed the trend sparkline. All of that is deterministic string/number
// work, so it lives here and the page stays presentational.

import type { UsageHistoryItem, UsageType } from '$lib/api/client';

/** A default reporting window ending now and starting `days` ago, as ISO strings. */
export function defaultRange(days: number, now: Date = new Date()): { from: string; to: string } {
	const to = now;
	const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
	return { from: from.toISOString(), to: to.toISOString() };
}

/** The unit a usage quantity is measured in, by type. */
export function unitFor(type: UsageType): string {
	switch (type) {
		case 'VOICE':
			return 'min';
		case 'DATA':
			return 'MB';
		case 'SMS':
			return 'SMS';
		default:
			return '';
	}
}

/** Format one record's quantity with its unit, e.g. `12 min`, `1,024 MB`. */
export function formatQuantity(item: UsageHistoryItem): string {
	const unit = unitFor(item.type);
	const value = item.quantity.toLocaleString('en-US');
	return unit ? `${value} ${unit}` : value;
}

export interface DailyTotal {
	/** ISO date (`YYYY-MM-DD`) in UTC. */
	date: string;
	/** Summed quantity for that day across all records in the series. */
	total: number;
	count: number;
}

/**
 * Roll a CDR series up into per-day totals, ascending by date. Days with no records
 * are not fabricated (the sparkline reads the present days); records are bucketed by
 * the UTC calendar date of `recordedAt`. Quantities are summed as-is - callers that
 * mix usage types should filter first, since summing minutes with MB is meaningless.
 */
export function dailyTotals(items: readonly UsageHistoryItem[]): DailyTotal[] {
	const buckets = new Map<string, DailyTotal>();
	for (const item of items) {
		const date = item.recordedAt.slice(0, 10);
		if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;
		const bucket = buckets.get(date) ?? { date, total: 0, count: 0 };
		bucket.total += item.quantity;
		bucket.count += 1;
		buckets.set(date, bucket);
	}
	return [...buckets.values()].sort((a, b) => a.date.localeCompare(b.date));
}
