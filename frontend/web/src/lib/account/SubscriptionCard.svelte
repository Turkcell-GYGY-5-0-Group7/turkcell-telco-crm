<script lang="ts">
	// One subscription on the /account view (16.5.2): its MSISDN, tariff code and
	// lifecycle status, plus a usage/quota gauge per metric. Usage is present only
	// for ACTIVE lines; suspended/terminated lines carry no live quota, so a plain
	// "no active quota" note is shown instead of an empty or fabricated gauge.
	import type { AccountSubscription } from '$lib/api/client';
	import { usageMetrics } from '$lib/account/usage';
	import UsageGauge from './UsageGauge.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Card from '$lib/ui/Card.svelte';
	import { subscriptionTone } from '$lib/ui/status';

	let { entry }: { entry: AccountSubscription } = $props();

	const metrics = $derived(entry.usage ? usageMetrics(entry.usage) : []);
</script>

<Card>
	<div class="body">
		<header>
			<div class="ids">
				<span class="msisdn">{entry.subscription.msisdn}</span>
				<span class="tariff">{entry.subscription.tariffCode}</span>
			</div>
			<Badge tone={subscriptionTone(entry.subscription.status)}>
				{entry.subscription.status}
			</Badge>
		</header>

		{#if metrics.length > 0}
			<div class="gauges">
				{#each metrics as metric (metric.label)}
					<UsageGauge {metric} />
				{/each}
			</div>
		{:else}
			<p class="no-quota">
				<svg viewBox="0 0 24 24" aria-hidden="true">
					<circle cx="12" cy="12" r="9" />
					<path d="M12 8v4.5M12 15.5v.5" />
				</svg>
				No active quota for this subscription.
			</p>
		{/if}
	</div>
</Card>

<style>
	.body {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-4);
	}

	.ids {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}

	.msisdn {
		font-family: var(--font-mono);
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 600;
	}

	.tariff {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	.gauges {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.no-quota {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	.no-quota svg {
		width: 1rem;
		height: 1rem;
		flex-shrink: 0;
		fill: none;
		stroke: currentColor;
		stroke-width: 1.8;
		stroke-linecap: round;
	}
</style>
