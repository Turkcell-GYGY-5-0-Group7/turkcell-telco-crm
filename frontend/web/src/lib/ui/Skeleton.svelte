<script lang="ts">
	// Loading placeholder shaped like the content it precedes, so the page does not
	// jump when the data lands. Always aria-hidden: the real loading announcement is
	// the page's aria-busy region, and a screen reader has nothing to gain from a
	// description of grey boxes. The shimmer is CSS-driven, so the global
	// reduced-motion guard in base.css stops it.
	let {
		variant = 'text',
		width,
		height,
		lines = 1
	}: {
		variant?: 'text' | 'block' | 'circle';
		width?: string;
		height?: string;
		/** For variant="text": how many lines to stack. The last one is shortened. */
		lines?: number;
	} = $props();

	const count = $derived(variant === 'text' ? Math.max(1, lines) : 1);
</script>

{#if variant === 'text'}
	<span class="lines" aria-hidden="true">
		{#each { length: count } as _, index (index)}
			<span
				class="sk text"
				class:last={index === count - 1 && count > 1}
				style:width={index === count - 1 && count > 1 ? undefined : width}
			></span>
		{/each}
	</span>
{:else}
	<span
		class={`sk ${variant}`}
		aria-hidden="true"
		style:width={width ?? undefined}
		style:height={height ?? undefined}
	></span>
{/if}

<style>
	.lines {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		width: 100%;
	}

	.sk {
		display: block;
		background: linear-gradient(
			90deg,
			var(--color-skeleton) 25%,
			var(--color-skeleton-shine) 37%,
			var(--color-skeleton) 63%
		);
		background-size: 400% 100%;
		animation: shimmer 1.4s ease-in-out infinite;
		border-radius: var(--radius-sm);
	}

	.text {
		width: 100%;
		height: 0.8rem;
	}

	.text.last {
		width: 60%;
	}

	.block {
		width: 100%;
		height: 3rem;
		border-radius: var(--radius-md);
	}

	.circle {
		width: 2.5rem;
		height: 2.5rem;
		border-radius: var(--radius-full);
	}

	@keyframes shimmer {
		0% {
			background-position: 100% 50%;
		}
		100% {
			background-position: 0% 50%;
		}
	}
</style>
