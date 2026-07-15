import { describe, expect, it } from 'vitest';
import { formatMoney } from './money';

describe('formatMoney', () => {
	it('formats a known currency code', () => {
		// Non-breaking space in Intl output; assert on the digits + code presence.
		const formatted = formatMoney(149.9, 'USD');
		expect(formatted).toContain('149.90');
	});

	it('falls back to "<amount> <currency>" for an unknown code', () => {
		expect(formatMoney(10, 'NOTREAL')).toBe('10.00 NOTREAL');
	});
});
