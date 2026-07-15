<script lang="ts">
	// "There is genuinely nothing here yet" - a legitimately empty list, not a
	// failure. Says what will appear here and, where there is one, offers the action
	// that fills it, so an empty page is never a dead end.
	import type { Snippet } from 'svelte';

	let {
		title,
		message,
		icon,
		action
	}: {
		title: string;
		message?: string;
		icon?: Snippet;
		action?: Snippet;
	} = $props();
</script>

<div class="empty">
	{#if icon}
		<span class="icon" aria-hidden="true">{@render icon()}</span>
	{/if}
	<h3>{title}</h3>
	{#if message}
		<p>{message}</p>
	{/if}
	{#if action}
		<div class="action">{@render action()}</div>
	{/if}
</div>

<style>
	.empty {
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

	.icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 3rem;
		height: 3rem;
		border-radius: var(--radius-full);
		background: var(--color-surface-alt);
		color: var(--color-text-muted);
	}

	h3 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	p {
		max-width: 32rem;
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}

	.action {
		margin-top: var(--space-2);
	}
</style>
