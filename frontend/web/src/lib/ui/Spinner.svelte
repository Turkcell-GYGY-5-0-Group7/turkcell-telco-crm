<script lang="ts">
	// Indeterminate activity indicator. Inherits currentColor so it reads correctly
	// on any surface (including inside a yellow primary button). When `label` is
	// given it announces itself; without one it is decorative and stays silent, so a
	// spinner inside an already-labelled control is not read out twice.
	let {
		size = 'md',
		label
	}: {
		size?: 'sm' | 'md';
		/** Accessible status text. Omit when the surrounding control already says it. */
		label?: string;
	} = $props();
</script>

<span class={`spinner ${size}`} role={label ? 'status' : undefined}>
	<svg viewBox="0 0 24 24" aria-hidden="true">
		<circle class="track" cx="12" cy="12" r="9" />
		<circle class="head" cx="12" cy="12" r="9" />
	</svg>
	{#if label}
		<span class="label">{label}</span>
	{/if}
</span>

<style>
	.spinner {
		display: inline-flex;
		align-items: center;
		gap: var(--space-3);
		color: inherit;
	}

	svg {
		width: 1rem;
		height: 1rem;
		animation: spin 800ms linear infinite;
	}

	.md svg {
		width: 1.5rem;
		height: 1.5rem;
	}

	circle {
		fill: none;
		stroke: currentColor;
		stroke-width: 2.5;
		stroke-linecap: round;
	}

	.track {
		opacity: 0.2;
	}

	.head {
		stroke-dasharray: 56;
		stroke-dashoffset: 42;
	}

	.label {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
</style>
