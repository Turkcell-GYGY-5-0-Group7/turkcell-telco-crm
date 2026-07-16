import { describe, expect, it } from 'vitest';
import type { UsageHistoryItem } from '$lib/api/client';
import { dailyTotals, defaultRange, formatQuantity, unitFor } from './history';

function record(partial: Partial<UsageHistoryItem>): UsageHistoryItem {
	return {
		id: 'r',
		subscriptionId: 's',
		type: 'DATA',
		quantity: 1,
		overage: false,
		cdrRef: 'ref',
		recordedAt: '2026-07-01T10:00:00.000Z',
		...partial
	};
}

describe('defaultRange', () => {
	it('spans `days` back from now as ISO strings', () => {
		const now = new Date('2026-07-15T12:00:00.000Z');
		const { from, to } = defaultRange(7, now);
		expect(to).toBe('2026-07-15T12:00:00.000Z');
		expect(from).toBe('2026-07-08T12:00:00.000Z');
	});
});

describe('unitFor / formatQuantity', () => {
	it('uses the right unit per type', () => {
		expect(unitFor('VOICE')).toBe('min');
		expect(unitFor('DATA')).toBe('MB');
		expect(unitFor('SMS')).toBe('SMS');
	});

	it('formats a quantity with a thousands separator and unit', () => {
		expect(formatQuantity(record({ type: 'DATA', quantity: 1024 }))).toBe('1,024 MB');
		expect(formatQuantity(record({ type: 'VOICE', quantity: 12 }))).toBe('12 min');
	});
});

describe('dailyTotals', () => {
	it('buckets records by UTC date and sums quantities, ascending', () => {
		const totals = dailyTotals([
			record({ recordedAt: '2026-07-02T09:00:00.000Z', quantity: 5 }),
			record({ recordedAt: '2026-07-01T23:00:00.000Z', quantity: 3 }),
			record({ recordedAt: '2026-07-02T21:00:00.000Z', quantity: 7 })
		]);
		expect(totals).toEqual([
			{ date: '2026-07-01', total: 3, count: 1 },
			{ date: '2026-07-02', total: 12, count: 2 }
		]);
	});

	it('returns an empty array for no records', () => {
		expect(dailyTotals([])).toEqual([]);
	});

	it('skips records with an unparseable timestamp', () => {
		expect(dailyTotals([record({ recordedAt: 'not-a-date' })])).toEqual([]);
	});
});
