<script lang="ts">
	// The single place toasts are rendered, mounted once in the root layout. The
	// region is aria-live="polite" (not "assertive"): a toast acknowledges something
	// the user just did, so it should be announced at the next natural pause rather
	// than cutting across whatever they are reading.
	import { flip } from 'svelte/animate';
	import { fly } from 'svelte/transition';
	import { motionDuration } from './motion';
	import { toasts } from './toast.svelte';
</script>

<div class="viewport" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div
			class={`toast ${toast.kind}`}
			in:fly={{ y: 12, duration: motionDuration(200) }}
			out:fly={{ y: 12, duration: motionDuration(150) }}
			animate:flip={{ duration: motionDuration(200) }}
		>
			<span class="bar" aria-hidden="true"></span>
			<span class="icon" aria-hidden="true">
				{#if toast.kind === 'success'}
					<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
				{:else if toast.kind === 'error'}
					<svg viewBox="0 0 24 24"
						><path d="M12 7v6M12 16.5v.5" /><circle cx="12" cy="12" r="9" /></svg
					>
				{:else}
					<svg viewBox="0 0 24 24"
						><path d="M12 11v6M12 7.5v.5" /><circle cx="12" cy="12" r="9" /></svg
					>
				{/if}
			</span>
			<p class="message">{toast.message}</p>
			<button
				type="button"
				class="dismiss"
				aria-label="Dismiss"
				onclick={() => toasts.dismiss(toast.id)}
			>
				<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
			</button>
		</div>
	{/each}
</div>

<style>
	.viewport {
		position: fixed;
		right: var(--space-4);
		bottom: var(--space-4);
		z-index: var(--z-toast);
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		width: min(24rem, calc(100vw - 2 * var(--space-4)));
		pointer-events: none;
	}

	.toast {
		position: relative;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		padding-left: var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-lg);
		overflow: hidden;
		pointer-events: auto;
	}

	.bar {
		position: absolute;
		inset: 0 auto 0 0;
		width: 4px;
	}

	.success .bar {
		background: var(--color-success-solid);
	}

	.error .bar {
		background: var(--color-danger-solid);
	}

	.info .bar {
		background: var(--color-info-solid);
	}

	.icon {
		display: inline-flex;
		flex-shrink: 0;
	}

	.icon svg {
		width: 1.15rem;
		height: 1.15rem;
		fill: none;
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	.success .icon svg {
		stroke: var(--color-success-solid);
	}

	.error .icon svg {
		stroke: var(--color-danger-solid);
	}

	.info .icon svg {
		stroke: var(--color-info-solid);
	}

	.message {
		flex: 1;
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
		color: var(--color-text);
	}

	.dismiss {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		width: 1.5rem;
		height: 1.5rem;
		padding: 0;
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.dismiss:hover {
		background: var(--color-surface-alt);
		color: var(--color-text);
	}

	.dismiss svg {
		width: 0.9rem;
		height: 0.9rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
	}

	@media (max-width: 30rem) {
		.viewport {
			left: var(--space-3);
			right: var(--space-3);
			bottom: var(--space-3);
			width: auto;
		}
	}
</style>
