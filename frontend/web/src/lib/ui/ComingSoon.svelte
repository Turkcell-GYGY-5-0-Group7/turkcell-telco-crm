<script lang="ts">
	// The single, honest treatment for a feature the platform does not expose an
	// endpoint for yet. It is NOT fake data: it says plainly that the capability is
	// planned, describes what it will do, and (optionally) offers a button that
	// acknowledges the click with an info toast rather than pretending to act. Using
	// one component everywhere keeps every unbuilt surface visually consistent.
	import Badge from './Badge.svelte';
	import Button from './Button.svelte';
	import { toasts } from './toast.svelte';

	let {
		title,
		message,
		cta
	}: {
		title: string;
		message?: string;
		/** Optional call-to-action label; clicking it shows a "not available yet" toast. */
		cta?: string;
	} = $props();

	function acknowledge() {
		toasts.info(`${title} is not available yet - this is a planned capability.`);
	}
</script>

<div class="coming-soon">
	<Badge tone="info">{#snippet children()}Planned{/snippet}</Badge>
	<h3>{title}</h3>
	{#if message}
		<p>{message}</p>
	{/if}
	{#if cta}
		<div class="cta">
			<Button variant="secondary" size="sm" onclick={acknowledge}>{cta}</Button>
		</div>
	{/if}
</div>

<style>
	.coming-soon {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: var(--space-3);
		padding: var(--space-10) var(--space-6);
		border: 1px dashed var(--color-border-strong);
		border-radius: var(--radius-lg);
		background: var(--color-surface);
	}

	h3 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	p {
		max-width: 34rem;
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}

	.cta {
		margin-top: var(--space-2);
	}
</style>
