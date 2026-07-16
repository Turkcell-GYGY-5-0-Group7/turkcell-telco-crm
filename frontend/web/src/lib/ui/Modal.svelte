<script lang="ts">
	// A centred dialog for a focused action: confirm a cancellation, edit a profile,
	// open a ticket. Follows the same overlay mechanics the app shell already uses
	// for its mobile drawer - a scrim that closes on click, Escape to close, and a
	// body scroll-lock while open - so the two surfaces behave identically.
	//
	// `open` is bindable; the caller owns it. Closing (scrim, Escape, the close
	// button) sets it false through `onclose`, so the parent stays the single source
	// of truth. Focus moves into the dialog on open for keyboard and screen-reader
	// users.
	import type { Snippet } from 'svelte';
	import { fade, scale } from 'svelte/transition';
	import { motionDuration } from './motion';

	let {
		open = $bindable(false),
		title,
		size = 'md',
		children,
		footer
	}: {
		open?: boolean;
		title: string;
		size?: 'sm' | 'md' | 'lg';
		children: Snippet;
		/** Optional trailing controls, typically the confirm/cancel buttons. */
		footer?: Snippet;
	} = $props();

	let dialog = $state<HTMLDivElement | null>(null);

	function close() {
		open = false;
	}

	// Move focus into the dialog when it opens so keyboard focus is not stranded
	// behind the scrim.
	$effect(() => {
		if (open && dialog) {
			dialog.focus();
		}
	});

	// While the dialog is open it owns the screen; the content behind must not scroll.
	$effect(() => {
		if (typeof document === 'undefined') return;
		document.body.style.overflow = open ? 'hidden' : '';
		return () => {
			document.body.style.overflow = '';
		};
	});

	function onWindowKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && open) {
			event.stopPropagation();
			close();
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

{#if open}
	<div class="overlay">
		<button
			type="button"
			class="scrim"
			aria-label="Close dialog"
			tabindex="-1"
			onclick={close}
			transition:fade={{ duration: motionDuration(150) }}
		></button>

		<div
			class={`dialog ${size}`}
			role="dialog"
			aria-modal="true"
			aria-label={title}
			tabindex="-1"
			bind:this={dialog}
			transition:scale={{ duration: motionDuration(180), start: 0.96 }}
		>
			<header class="dialog-head">
				<h2>{title}</h2>
				<button type="button" class="close" aria-label="Close" onclick={close}>
					<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
				</button>
			</header>

			<div class="dialog-body">
				{@render children()}
			</div>

			{#if footer}
				<footer class="dialog-foot">{@render footer()}</footer>
			{/if}
		</div>
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		z-index: var(--z-drawer);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: var(--space-4);
	}

	.scrim {
		position: fixed;
		inset: 0;
		border: 0;
		padding: 0;
		background: rgb(10 15 31 / 0.55);
		cursor: pointer;
	}

	.dialog {
		position: relative;
		display: flex;
		flex-direction: column;
		width: 100%;
		max-height: calc(100vh - var(--space-8));
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg);
		overflow: hidden;
	}

	.dialog:focus {
		outline: none;
	}

	.sm {
		max-width: 24rem;
	}
	.md {
		max-width: 32rem;
	}
	.lg {
		max-width: 44rem;
	}

	.dialog-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-5) var(--space-6);
		border-bottom: 1px solid var(--color-border);
	}

	.dialog-head h2 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	.close {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2rem;
		height: 2rem;
		padding: 0;
		border: 0;
		border-radius: var(--radius-md);
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.close:hover {
		background: var(--color-surface-alt);
		color: var(--color-text);
	}

	.close svg {
		width: 1.1rem;
		height: 1.1rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
	}

	.dialog-body {
		padding: var(--space-6);
		overflow-y: auto;
	}

	.dialog-foot {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-6);
		border-top: 1px solid var(--color-border);
		background: var(--color-surface-alt);
	}
</style>
