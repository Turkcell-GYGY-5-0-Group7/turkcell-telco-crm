<script lang="ts">
	// A single usage/quota bar for one metric (data / voice / SMS) on the /account
	// view (16.5.2). Presentational only: the percentage and label are computed by
	// the pure, unit-tested $lib/account/usage module. The bar is exposed as an
	// ARIA progressbar so the indicator is accessible.
	//
	// The fill warms from navy to amber to red as the allowance runs out (thresholds
	// in $lib/ui/status). Colour is never the only signal - the "used / allowance"
	// figure sits directly above the bar.
	import { formatUsageLabel, usagePercent, type UsageMetric } from '$lib/account/usage';
	import { gaugeTone } from '$lib/ui/status';

	let { metric }: { metric: UsageMetric } = $props();

	const percent = $derived(usagePercent(metric.used, metric.allowance));
	const label = $derived(formatUsageLabel(metric));
	const tone = $derived(gaugeTone(percent));
</script>

<div class="gauge">
	<div class="row">
		<span class="name">{metric.label}</span>
		<span class="value tabular">{label}</span>
	</div>
	<div
		class="track"
		role="progressbar"
		aria-label={`${metric.label} usage`}
		aria-valuemin={0}
		aria-valuemax={100}
		aria-valuenow={Math.round(percent)}
	>
		<div class={`fill ${tone}`} style={`width: ${percent}%`}></div>
	</div>
</div>

<style>
	.gauge {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.row {
		display: flex;
		justify-content: space-between;
		gap: var(--space-3);
		font-size: var(--text-sm-size);
	}

	.name {
		font-weight: 600;
		color: var(--color-text);
	}

	.value {
		color: var(--color-text-muted);
	}

	.track {
		height: 0.5rem;
		border-radius: var(--radius-full);
		background: var(--color-surface-alt);
		box-shadow: inset 0 0 0 1px var(--color-border);
		overflow: hidden;
	}

	.fill {
		height: 100%;
		border-radius: var(--radius-full);
		transition: width var(--duration-slow) var(--ease-out);
	}

	.fill.ok {
		background: var(--gauge-ok);
	}

	.fill.warning {
		background: var(--color-warning-solid);
	}

	.fill.danger {
		background: var(--color-danger-solid);
	}
</style>
