<script lang="ts">
	// Every page opens with one of these, so the h1 lands in the same place with the
	// same weight throughout the app. The short yellow rule above the title is the
	// brand's only appearance on a light surface where it is not a fill - it is
	// decorative, never text, which is what keeps it clear of the contrast floor.
	import type { Snippet } from 'svelte';

	let {
		title,
		subtitle,
		actions
	}: {
		title: string;
		subtitle?: string;
		/** Optional page-level controls, right-aligned against the title. */
		actions?: Snippet;
	} = $props();
</script>

<header class="page-header">
	<div class="titles">
		<span class="rule" aria-hidden="true"></span>
		<h1>{title}</h1>
		{#if subtitle}
			<p class="subtitle">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="actions">{@render actions()}</div>
	{/if}
</header>

<style>
	.page-header {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
		gap: var(--space-4);
		flex-wrap: wrap;
	}

	.titles {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.rule {
		width: 2.25rem;
		height: 3px;
		border-radius: var(--radius-full);
		background: var(--color-accent);
	}

	h1 {
		font-size: var(--text-2xl-size);
		line-height: var(--text-2xl-lh);
		font-weight: 700;
	}

	.subtitle {
		color: var(--color-text-secondary);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}
</style>
