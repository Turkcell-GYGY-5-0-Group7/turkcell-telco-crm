<script lang="ts">
	// The app's surface container: every panel, table wrapper and wizard step sits in
	// one, so elevation and border treatment stay consistent across the app.
	import type { Snippet } from 'svelte';

	let {
		padding = 'md',
		header,
		footer,
		children
	}: {
		padding?: 'md' | 'lg' | 'none';
		/** Optional top region, separated by a hairline. */
		header?: Snippet;
		/** Optional bottom region, separated by a hairline. */
		footer?: Snippet;
		children: Snippet;
	} = $props();
</script>

<div class="card">
	{#if header}
		<div class={`region head ${padding}`}>{@render header()}</div>
	{/if}
	<div class={`region body ${padding}`}>{@render children()}</div>
	{#if footer}
		<div class={`region foot ${padding}`}>{@render footer()}</div>
	{/if}
</div>

<style>
	.card {
		display: flex;
		flex-direction: column;
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		overflow: hidden;
	}

	.md {
		padding: var(--space-5);
	}

	.lg {
		padding: var(--space-6);
	}

	.none {
		padding: 0;
	}

	.head {
		border-bottom: 1px solid var(--color-border);
	}

	.foot {
		border-top: 1px solid var(--color-border);
		background: var(--color-surface-alt);
	}
</style>
