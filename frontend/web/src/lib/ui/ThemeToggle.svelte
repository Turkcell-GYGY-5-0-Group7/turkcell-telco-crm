<script lang="ts">
	// Light/dark switch. State and persistence live in $lib/theme; this is only the
	// control. The label names the DESTINATION ("Switch to dark theme"), not the
	// current state, because that is what pressing it does.
	import { theme } from '$lib/theme/theme.svelte';

	const isDark = $derived(theme.current === 'dark');
	const label = $derived(isDark ? 'Switch to light theme' : 'Switch to dark theme');
</script>

<button
	type="button"
	class="toggle"
	aria-label={label}
	title={label}
	onclick={() => theme.toggle()}
>
	{#if isDark}
		<svg viewBox="0 0 24 24" aria-hidden="true">
			<circle cx="12" cy="12" r="4.2" />
			<path
				d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4"
			/>
		</svg>
	{:else}
		<svg viewBox="0 0 24 24" aria-hidden="true">
			<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z" />
		</svg>
	{/if}
</button>

<style>
	.toggle {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2.25rem;
		height: 2.25rem;
		padding: 0;
		border: 1px solid transparent;
		border-radius: var(--radius-md);
		background: transparent;
		color: var(--color-header-muted);
		cursor: pointer;
		transition:
			background-color var(--duration-fast) var(--ease-out),
			color var(--duration-fast) var(--ease-out);
	}

	.toggle:hover {
		background: rgb(255 255 255 / 0.08);
		color: var(--color-header-text);
	}

	svg {
		width: 1.15rem;
		height: 1.15rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 1.8;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
