import { describe, expect, it } from 'vitest';
import { toSparklineGeometry } from './sparkline';

describe('toSparklineGeometry', () => {
	it('returns empty geometry for an empty series', () => {
		expect(toSparklineGeometry([], 100, 20)).toEqual({ points: '', coordinates: [] });
	});

	it('draws a single point down the middle (steady, not zero)', () => {
		const { coordinates } = toSparklineGeometry([42], 100, 20);
		expect(coordinates).toEqual([[50, 10]]);
	});

	it('spans the first point to x=0 and the last to x=width', () => {
		const { coordinates } = toSparklineGeometry([1, 2, 3], 100, 20);
		expect(coordinates[0][0]).toBe(0);
		expect(coordinates[coordinates.length - 1][0]).toBe(100);
	});

	it('inverts y so the maximum value sits highest (smallest y)', () => {
		const { coordinates } = toSparklineGeometry([0, 10], 100, 20, 0);
		// value 0 -> bottom (y=20), value 10 -> top (y=0)
		expect(coordinates[0][1]).toBe(20);
		expect(coordinates[1][1]).toBe(0);
	});

	it('draws a flat series along the midline', () => {
		const { coordinates } = toSparklineGeometry([5, 5, 5], 100, 20);
		expect(coordinates.every(([, y]) => y === 10)).toBe(true);
	});

	it('ignores non-finite values', () => {
		const { coordinates } = toSparklineGeometry([1, NaN, 3], 100, 20);
		expect(coordinates).toHaveLength(2);
	});

	it('produces a space-separated points string', () => {
		const { points } = toSparklineGeometry([1, 2], 10, 10, 0);
		expect(points).toMatch(/^[\d.]+,[\d.]+ [\d.]+,[\d.]+$/);
	});
});
