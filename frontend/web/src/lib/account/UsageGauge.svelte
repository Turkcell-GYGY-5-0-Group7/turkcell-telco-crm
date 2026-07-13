<script lang="ts">
	// A single usage/quota bar for one metric (data / voice / SMS) on the /account
	// view (16.5.2). Presentational only: the percentage and label are computed by
	// the pure, unit-tested $lib/account/usage module. The bar is exposed as an
	// ARIA progressbar so the indicator is accessible.
	import { formatUsageLabel, usagePercent, type UsageMetric } from '$lib/account/usage';

	let { metric }: { metric: UsageMetric } = $props();

	const percent = $derived(usagePercent(metric.used, metric.allowance));
	const label = $derived(formatUsageLabel(metric));
</script>

<div class="gauge">
	<div class="row">
		<span class="name">{metric.label}</span>
		<span class="value">{label}</span>
	</div>
	<div
		class="track"
		role="progressbar"
		aria-label={`${metric.label} usage`}
		aria-valuemin={0}
		aria-valuemax={100}
		aria-valuenow={Math.round(percent)}
	>
		<div class="fill" style={`width: ${percent}%`}></div>
	</div>
</div>

<style>
	.gauge {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
	}

	.row {
		display: flex;
		justify-content: space-between;
		font-size: 0.85rem;
		color: #374151;
	}

	.name {
		font-weight: 600;
	}

	.value {
		color: #4b5563;
	}

	.track {
		height: 0.5rem;
		border-radius: 999px;
		background: #e5e7eb;
		overflow: hidden;
	}

	.fill {
		height: 100%;
		background: #16213e;
		border-radius: 999px;
	}
</style>
