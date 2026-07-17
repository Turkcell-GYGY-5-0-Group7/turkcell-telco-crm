<script lang="ts" generics="T">
	// The app's data table. It renders whatever rows it is handed; the caller draws
	// each cell through the `cell` snippet, so the table owns layout and states
	// (header, hover, skeleton, empty) while the page owns formatting and badges.
	//
	// Sorting is display-only over the current rows (see $lib/ui/table-sort): a
	// sortable header cycles asc -> desc -> off. For a server-paged list the caller
	// simply leaves columns unsortable and orders server-side. Narrow viewports
	// scroll the table sideways rather than reflowing, so a row stays one comparable
	// line of facts.
	import type { Snippet } from 'svelte';
	import { nextSortState, sortRows, type SortState } from './table-sort';
	import Skeleton from './Skeleton.svelte';

	interface Column {
		key: string;
		label: string;
		sortable?: boolean;
		align?: 'left' | 'right' | 'center';
		/** Optional fixed column width (any CSS length). */
		width?: string;
	}

	let {
		columns,
		rows,
		rowKey,
		loading = false,
		skeletonRows = 5,
		cell,
		empty
	}: {
		columns: Column[];
		rows: T[];
		/** Stable key per row for keyed iteration. */
		rowKey: (row: T) => string;
		loading?: boolean;
		skeletonRows?: number;
		/** Draws one cell. Receives the row and the active column key. */
		cell: Snippet<[T, string]>;
		/** Shown when there are no rows and we are not loading. */
		empty?: Snippet;
	} = $props();

	let sort = $state<SortState | null>(null);

	const displayRows = $derived(sortRows(rows, sort));

	function toggle(column: Column) {
		if (!column.sortable) return;
		sort = nextSortState(sort, column.key);
	}

	function ariaSort(column: Column): 'ascending' | 'descending' | 'none' | undefined {
		if (!column.sortable) return undefined;
		if (sort?.key !== column.key) return 'none';
		return sort.dir === 'asc' ? 'ascending' : 'descending';
	}
</script>

<div class="table-scroll">
	<table>
		<thead>
			<tr>
				{#each columns as column (column.key)}
					<th
						scope="col"
						class={column.align ?? 'left'}
						style={column.width ? `width:${column.width}` : undefined}
						aria-sort={ariaSort(column)}
					>
						{#if column.sortable}
							<button type="button" class="sort" onclick={() => toggle(column)}>
								<span>{column.label}</span>
								<span class="arrow" aria-hidden="true">
									{#if sort?.key === column.key}
										{sort.dir === 'asc' ? '↑' : '↓'}
									{:else}
										{'↕'}
									{/if}
								</span>
							</button>
						{:else}
							{column.label}
						{/if}
					</th>
				{/each}
			</tr>
		</thead>
		<tbody>
			{#if loading}
				{#each Array.from({ length: skeletonRows }, (_, i) => i) as index (index)}
					<tr>
						{#each columns as column (column.key)}
							<td class={column.align ?? 'left'}><Skeleton variant="text" width="70%" /></td>
						{/each}
					</tr>
				{/each}
			{:else}
				{#each displayRows as row (rowKey(row))}
					<tr>
						{#each columns as column (column.key)}
							<td class={column.align ?? 'left'}>{@render cell(row, column.key)}</td>
						{/each}
					</tr>
				{/each}
			{/if}
		</tbody>
	</table>

	{#if !loading && displayRows.length === 0 && empty}
		<div class="empty-slot">{@render empty()}</div>
	{/if}
</div>

<style>
	.table-scroll {
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: collapse;
	}

	th {
		text-align: left;
		padding: var(--space-3) var(--space-4);
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
		background: var(--color-surface-alt);
		border-bottom: 1px solid var(--color-border);
		white-space: nowrap;
	}

	th.right,
	td.right {
		text-align: right;
	}

	th.center,
	td.center {
		text-align: center;
	}

	td {
		padding: var(--space-3) var(--space-4);
		font-size: var(--text-sm-size);
		border-bottom: 1px solid var(--color-border);
		vertical-align: middle;
	}

	tbody tr:hover {
		background: var(--color-surface-alt);
	}

	tbody tr:last-child td {
		border-bottom: 0;
	}

	.sort {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: 0;
		border: 0;
		background: none;
		font: inherit;
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: inherit;
		letter-spacing: inherit;
		color: inherit;
		cursor: pointer;
	}

	.sort:hover {
		color: var(--color-text);
	}

	.arrow {
		font-size: 0.85em;
		opacity: 0.7;
	}

	.empty-slot {
		padding: var(--space-8) var(--space-4);
	}
</style>
