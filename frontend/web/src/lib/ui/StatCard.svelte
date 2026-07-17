<script lang="ts">
	// A single KPI tile: a label, a big value, and an optional hint. `value` is
	// `string | null`; null renders a skeleton, so a dashboard can lay out its tiles
	// before the numbers arrive. An optional accent stripe (tone) lets a row of tiles
	// carry a little status colour without turning into badges.
	import type { BadgeTone } from './status';
	import Skeleton from './Skeleton.svelte';

	let {
		label,
		value,
		hint,
		tone = 'neutral'
	}: {
		label: string;
		/** The metric; null shows a skeleton while it loads. */
		value: string | null;
		hint?: string;
		tone?: BadgeTone;
	} = $props();
</script>

<div class={`stat ${tone}`}>
	<span class="stat-label">{label}</span>
	{#if value === null}
		<div class="stat-value"><Skeleton variant="text" width="60%" /></div>
	{:else}
		<span class="stat-value">{value}</span>
	{/if}
	{#if hint}
		<span class="stat-hint">{hint}</span>
	{/if}
</div>

<style>
	.stat {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-5);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-left: 3px solid var(--color-border-strong);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
	}

	.stat.success {
		border-left-color: var(--color-success-solid);
	}
	.stat.warning {
		border-left-color: var(--color-warning-solid);
	}
	.stat.danger {
		border-left-color: var(--color-danger-solid);
	}
	.stat.info {
		border-left-color: var(--color-info-solid);
	}

	.stat-label {
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}

	.stat-value {
		font-size: var(--text-2xl-size);
		line-height: var(--text-2xl-lh);
		font-weight: 700;
		color: var(--color-text);
	}

	.stat-hint {
		font-size: var(--text-xs-size);
		color: var(--color-text-secondary);
	}
</style>
