import { describe, expect, it } from 'vitest';
import { compareValues, nextSortState, sortRows, type SortState } from './table-sort';

describe('compareValues', () => {
	it('orders numbers numerically', () => {
		expect(compareValues(2, 10)).toBeLessThan(0);
		expect(compareValues(10, 2)).toBeGreaterThan(0);
		expect(compareValues(5, 5)).toBe(0);
	});

	it('orders strings with natural numeric awareness', () => {
		expect(compareValues('item-2', 'item-10')).toBeLessThan(0);
		expect(compareValues('B', 'a')).toBeGreaterThan(0);
	});

	it('sinks missing values to the end regardless of the other operand', () => {
		expect(compareValues(null, 5)).toBeGreaterThan(0);
		expect(compareValues(5, null)).toBeLessThan(0);
		expect(compareValues('', 'a')).toBeGreaterThan(0);
		expect(compareValues(undefined, undefined)).toBe(0);
	});
});

describe('sortRows', () => {
	const rows = [
		{ n: 3, s: 'c' },
		{ n: 1, s: 'a' },
		{ n: 2, s: 'b' }
	];

	it('returns a copy and does not mutate the input', () => {
		const sorted = sortRows(rows, { key: 'n', dir: 'asc' });
		expect(sorted.map((r) => r.n)).toEqual([1, 2, 3]);
		expect(rows.map((r) => r.n)).toEqual([3, 1, 2]);
	});

	it('sorts descending', () => {
		expect(sortRows(rows, { key: 'n', dir: 'desc' }).map((r) => r.n)).toEqual([3, 2, 1]);
	});

	it('returns the natural order for a null state', () => {
		expect(sortRows(rows, null).map((r) => r.n)).toEqual([3, 1, 2]);
	});

	it('keeps missing values last in both directions', () => {
		const mixed = [{ n: 2 }, { n: null }, { n: 1 }];
		expect(sortRows(mixed, { key: 'n', dir: 'asc' }).map((r) => r.n)).toEqual([1, 2, null]);
		expect(sortRows(mixed, { key: 'n', dir: 'desc' }).map((r) => r.n)).toEqual([2, 1, null]);
	});

	it('supports a custom accessor', () => {
		const data = [{ meta: { score: 9 } }, { meta: { score: 4 } }];
		const sorted = sortRows(
			data,
			{ key: 'score', dir: 'asc' },
			(row, key) => row.meta[key as 'score']
		);
		expect(sorted.map((r) => r.meta.score)).toEqual([4, 9]);
	});
});

describe('nextSortState', () => {
	it('sorts a fresh column ascending', () => {
		expect(nextSortState(null, 'name')).toEqual({ key: 'name', dir: 'asc' });
	});

	it('switches to a newly clicked column ascending', () => {
		const current: SortState = { key: 'name', dir: 'desc' };
		expect(nextSortState(current, 'date')).toEqual({ key: 'date', dir: 'asc' });
	});

	it('cycles asc -> desc -> cleared on the active column', () => {
		let state: SortState | null = { key: 'name', dir: 'asc' };
		state = nextSortState(state, 'name');
		expect(state).toEqual({ key: 'name', dir: 'desc' });
		state = nextSortState(state, 'name');
		expect(state).toBeNull();
	});
});
