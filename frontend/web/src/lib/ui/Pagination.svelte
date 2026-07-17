<script lang="ts">
	// Prev / "Page x of y" / Next controls, extracted from the invoices pager so
	// every paged list uses the same affordance. The caller owns paging state and
	// passes the zero-based page and total page count plus the two callbacks; this
	// only computes whether each direction is available.
	import Button from './Button.svelte';

	let {
		page,
		totalPages,
		disabled = false,
		onPrev,
		onNext
	}: {
		/** Zero-based current page index. */
		page: number;
		totalPages: number;
		/** Disable both directions (e.g. while a page is loading). */
		disabled?: boolean;
		onPrev: () => void;
		onNext: () => void;
	} = $props();

	const canPrev = $derived(page > 0 && !disabled);
	const canNext = $derived(page < totalPages - 1 && !disabled);
</script>

<div class="pager">
	<Button variant="secondary" size="sm" disabled={!canPrev} onclick={onPrev}>
		<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 5.5L8 12l6.5 6.5" /></svg>
		Previous
	</Button>
	<span class="page-label tabular">Page {page + 1} of {Math.max(totalPages, 1)}</span>
	<Button variant="secondary" size="sm" disabled={!canNext} onclick={onNext}>
		Next
		<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 5.5L16 12l-6.5 6.5" /></svg>
	</Button>
</div>

<style>
	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-4);
	}

	.page-label {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}

	.pager svg {
		width: 0.9rem;
		height: 0.9rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
