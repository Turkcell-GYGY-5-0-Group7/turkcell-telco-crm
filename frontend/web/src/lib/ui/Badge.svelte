<script lang="ts">
	// Status pill. Tone is chosen by the caller from $lib/ui/status, never derived
	// from a raw status string here - so an unknown status still renders a legible
	// neutral badge instead of an unstyled one.
	//
	// Colour never carries the meaning alone: the badge always shows the status text
	// as well, which is what makes it readable to colour-blind users.
	import type { Snippet } from 'svelte';
	import type { BadgeTone } from './status';

	let {
		tone = 'neutral',
		children
	}: {
		tone?: BadgeTone;
		children: Snippet;
	} = $props();
</script>

<span class={`badge ${tone}`}>{@render children()}</span>

<style>
	.badge {
		display: inline-flex;
		align-items: center;
		padding: 0.2rem 0.6rem;
		border-radius: var(--radius-full);
		font-size: var(--text-xs-size);
		font-weight: 600;
		line-height: var(--text-xs-lh);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		white-space: nowrap;
	}

	.neutral {
		background: var(--color-surface-alt);
		color: var(--color-text-secondary);
		box-shadow: inset 0 0 0 1px var(--color-border);
	}

	.success {
		background: var(--color-success-bg);
		color: var(--color-success-text);
	}

	.warning {
		background: var(--color-warning-bg);
		color: var(--color-warning-text);
	}

	.danger {
		background: var(--color-danger-bg);
		color: var(--color-danger-text);
	}

	.info {
		background: var(--color-info-bg);
		color: var(--color-info-text);
	}
</style>
