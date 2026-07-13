import { describe, expect, it } from 'vitest';
import { formatUsageLabel, usageMetrics, usagePercent } from './usage';
import type { UsageSummary } from '$lib/api/client';

describe('usagePercent', () => {
	it('computes the consumed share of the allowance', () => {
		expect(usagePercent(50, 200)).toBe(25);
	});

	it('clamps overage to 100', () => {
		expect(usagePercent(300, 200)).toBe(100);
	});

	it('returns 0 for a zero or negative allowance (no quota, no divide-by-zero)', () => {
		expect(usagePercent(10, 0)).toBe(0);
		expect(usagePercent(10, -5)).toBe(0);
	});

	it('returns 0 for a non-finite allowance', () => {
		expect(usagePercent(10, Number.NaN)).toBe(0);
		expect(usagePercent(10, Number.POSITIVE_INFINITY)).toBe(0);
	});

	it('floors negative usage at 0', () => {
		expect(usagePercent(-5, 100)).toBe(0);
	});
});

describe('usageMetrics', () => {
	const usage: UsageSummary = {
		dataUsedMb: 1024,
		dataAllowanceMb: 5120,
		voiceUsedMinutes: 120,
		voiceAllowanceMinutes: 500,
		smsUsed: 40,
		smsAllowance: 100
	};

	it('maps a UsageSummary into the three gauge metrics in order', () => {
		const metrics = usageMetrics(usage);
		expect(metrics.map((m) => m.label)).toEqual(['Data', 'Voice', 'SMS']);
		expect(metrics.map((m) => m.unit)).toEqual(['MB', 'min', 'SMS']);
		expect(metrics[0]).toMatchObject({ used: 1024, allowance: 5120 });
		expect(metrics[1]).toMatchObject({ used: 120, allowance: 500 });
		expect(metrics[2]).toMatchObject({ used: 40, allowance: 100 });
	});
});

describe('formatUsageLabel', () => {
	it('renders a grouped "used / allowance unit" label', () => {
		expect(formatUsageLabel({ label: 'Data', used: 1024, allowance: 5120, unit: 'MB' })).toBe(
			'1,024 / 5,120 MB'
		);
	});
});
