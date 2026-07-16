// Pure client-side sorting for the shared Table (unit tested in Node).
//
// The Table renders whatever rows it is given; when a column is marked sortable
// the header cycles a sort state through this module and re-orders a COPY of the
// rows. Kept framework-agnostic so the cycle logic and the comparator are proven
// without a DOM. Sorting here is display-only over the current page of data - it
// never substitutes for server-side ordering of a full result set.

export type SortDir = 'asc' | 'desc';

export interface SortState {
	key: string;
	dir: SortDir;
}

/**
 * Compare two cell values for a stable, human-friendly order. Numbers sort
 * numerically, everything else by locale-aware string compare; null/undefined
 * always sink to the end regardless of direction, so "no value" never masquerades
 * as the smallest or largest real value.
 */
export function compareValues(a: unknown, b: unknown): number {
	const aMissing = a === null || a === undefined || a === '';
	const bMissing = b === null || b === undefined || b === '';
	if (aMissing && bMissing) return 0;
	if (aMissing) return 1;
	if (bMissing) return -1;

	if (typeof a === 'number' && typeof b === 'number') {
		return a - b;
	}
	return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' });
}

/**
 * Return a sorted COPY of `rows` for the given state (the input is never mutated).
 * `key` is read via `accessor`, defaulting to a plain property lookup. Missing
 * values always sink to the end (see {@link compareValues}), so descending order
 * flips only the present values.
 */
export function sortRows<T>(
	rows: readonly T[],
	state: SortState | null,
	accessor: (row: T, key: string) => unknown = (row, key) => (row as Record<string, unknown>)[key]
): T[] {
	if (!state) return [...rows];
	const factor = state.dir === 'asc' ? 1 : -1;
	return [...rows].sort((left, right) => {
		const a = accessor(left, state.key);
		const b = accessor(right, state.key);
		// Sink missing values to the end in BOTH directions: the direction factor
		// must reorder only present values, never flip "no value" to the top.
		const aMissing = a === null || a === undefined || a === '';
		const bMissing = b === null || b === undefined || b === '';
		if (aMissing && bMissing) return 0;
		if (aMissing) return 1;
		if (bMissing) return -1;
		return compareValues(a, b) * factor;
	});
}

/**
 * Advance the sort state when a column header is activated. Clicking a new column
 * sorts it ascending; clicking the active column flips asc -> desc, then desc
 * clears the sort (back to the data's natural order). This three-step cycle is the
 * familiar spreadsheet behaviour.
 */
export function nextSortState(current: SortState | null, key: string): SortState | null {
	if (!current || current.key !== key) {
		return { key, dir: 'asc' };
	}
	if (current.dir === 'asc') {
		return { key, dir: 'desc' };
	}
	return null;
}
