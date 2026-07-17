<script lang="ts">
	// A tiny inline trend line, drawn as a single SVG polyline (no chart library -
	// see $lib/ui/sparkline for the geometry). Used for the daily usage trend on the
	// usage page. Decorative by default; pass an `ariaLabel` to give it meaning for
	// screen readers, otherwise it is hidden from them.
	import { toSparklineGeometry } from './sparkline';

	let {
		values,
		width = 160,
		height = 40,
		ariaLabel
	}: {
		values: number[];
		width?: number;
		height?: number;
		ariaLabel?: string;
	} = $props();

	const geometry = $derived(toSparklineGeometry(values, width, height, 2));
	const lastPoint = $derived(geometry.coordinates.at(-1) ?? null);
</script>

<svg
	class="sparkline"
	viewBox={`0 0 ${width} ${height}`}
	preserveAspectRatio="none"
	role={ariaLabel ? 'img' : 'presentation'}
	aria-label={ariaLabel}
	aria-hidden={ariaLabel ? undefined : 'true'}
>
	{#if geometry.points}
		<polyline points={geometry.points} fill="none" />
		{#if lastPoint}
			<circle cx={lastPoint[0]} cy={lastPoint[1]} r="2.5" />
		{/if}
	{/if}
</svg>

<style>
	.sparkline {
		display: block;
		width: 100%;
		height: auto;
		overflow: visible;
	}

	polyline {
		stroke: var(--color-accent);
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
		vector-effect: non-scaling-stroke;
	}

	circle {
		fill: var(--color-accent);
	}
</style>
