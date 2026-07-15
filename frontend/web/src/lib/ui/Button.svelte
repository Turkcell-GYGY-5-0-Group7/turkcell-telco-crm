<script lang="ts">
	// The app's single button. Renders an <a> when `href` is given and a <button>
	// otherwise, so a navigation and an action look identical without faking either
	// (a div-with-onclick would lose keyboard and middle-click behaviour).
	//
	// `loading` disables the control and swaps its label for a spinner while keeping
	// the button's width, so a row of buttons does not reflow mid-request.
	import type { Snippet } from 'svelte';
	import Spinner from './Spinner.svelte';

	let {
		variant = 'primary',
		size = 'md',
		href,
		type = 'button',
		disabled = false,
		loading = false,
		onclick,
		children
	}: {
		/** primary = the yellow brand fill; secondary = outlined; ghost = bare; danger = destructive. */
		variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
		size?: 'sm' | 'md';
		/** When set, renders a link styled as a button. */
		href?: string;
		type?: 'button' | 'submit';
		disabled?: boolean;
		/** Shows a spinner in place of the label and blocks interaction. */
		loading?: boolean;
		onclick?: (event: MouseEvent) => void;
		children: Snippet;
	} = $props();

	const inert = $derived(disabled || loading);
</script>

{#if href}
	<a
		{href}
		class={`btn ${variant} ${size}`}
		class:is-loading={loading}
		aria-disabled={inert ? 'true' : undefined}
		{onclick}
	>
		{@render children()}
	</a>
{:else}
	<button
		{type}
		class={`btn ${variant} ${size}`}
		class:is-loading={loading}
		disabled={inert}
		aria-busy={loading ? 'true' : undefined}
		{onclick}
	>
		{#if loading}
			<span class="spinner"><Spinner size="sm" /></span>
		{/if}
		<span class="label">{@render children()}</span>
	</button>
{/if}

<style>
	.btn {
		position: relative;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		font: inherit;
		font-weight: 600;
		text-decoration: none;
		white-space: nowrap;
		border: 1px solid transparent;
		border-radius: var(--radius-md);
		cursor: pointer;
		transition:
			background-color var(--duration-fast) var(--ease-out),
			border-color var(--duration-fast) var(--ease-out),
			color var(--duration-fast) var(--ease-out),
			transform var(--duration-fast) var(--ease-out);
	}

	.btn:active:not(:disabled) {
		transform: translateY(1px);
	}

	.md {
		padding: 0.55rem 1.15rem;
		font-size: var(--text-sm-size);
	}

	.sm {
		padding: 0.35rem 0.8rem;
		font-size: var(--text-xs-size);
	}

	.primary {
		background: var(--color-accent);
		border-color: var(--color-accent);
		color: var(--color-on-accent);
	}

	.primary:hover:not(:disabled) {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}

	.secondary {
		background: var(--color-surface);
		border-color: var(--color-border-strong);
		color: var(--color-text);
	}

	.secondary:hover:not(:disabled) {
		background: var(--color-surface-alt);
		border-color: var(--color-text-muted);
	}

	.ghost {
		background: transparent;
		color: var(--color-text-secondary);
	}

	.ghost:hover:not(:disabled) {
		background: var(--color-surface-alt);
		color: var(--color-text);
	}

	.danger {
		background: var(--color-danger-solid);
		border-color: var(--color-danger-solid);
		color: #ffffff;
	}

	.danger:hover:not(:disabled) {
		filter: brightness(0.94);
	}

	.btn:disabled,
	.btn[aria-disabled='true'] {
		opacity: 0.55;
		cursor: default;
		transform: none;
	}

	/* Keep the label in the layout so the button does not shrink around the spinner. */
	.is-loading .label {
		visibility: hidden;
	}

	.spinner {
		position: absolute;
		display: inline-flex;
	}
</style>
