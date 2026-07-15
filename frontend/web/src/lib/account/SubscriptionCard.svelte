<script lang="ts">
	// One subscription on the /account view (16.5.2): its MSISDN, tariff code and
	// lifecycle status, plus a usage/quota gauge per metric. Usage is present only
	// for ACTIVE lines; suspended/terminated lines carry no live quota, so a plain
	// "no active quota" note is shown instead of an empty or fabricated gauge.
	import type { AccountSubscription } from '$lib/api/client';
	import { usageMetrics } from '$lib/account/usage';
	import UsageGauge from './UsageGauge.svelte';

	let { entry }: { entry: AccountSubscription } = $props();

	const metrics = $derived(entry.usage ? usageMetrics(entry.usage) : []);
	const statusClass = $derived(entry.subscription.status.toLowerCase());
</script>

<article class="card">
	<header>
		<div class="ids">
			<span class="msisdn">{entry.subscription.msisdn}</span>
			<span class="tariff">{entry.subscription.tariffCode}</span>
		</div>
		<span class={`status ${statusClass}`}>{entry.subscription.status}</span>
	</header>

	{#if metrics.length > 0}
		<div class="gauges">
			{#each metrics as metric (metric.label)}
				<UsageGauge {metric} />
			{/each}
		</div>
	{:else}
		<p class="no-quota">No active quota for this subscription.</p>
	{/if}
</article>

<style>
	.card {
		background: #ffffff;
		border: 1px solid #e5e7eb;
		border-radius: 0.75rem;
		padding: 1.25rem;
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
	}

	.ids {
		display: flex;
		flex-direction: column;
		gap: 0.15rem;
	}

	.msisdn {
		font-weight: 600;
		font-size: 1.05rem;
	}

	.tariff {
		color: #6b7280;
		font-size: 0.85rem;
	}

	.status {
		font-size: 0.75rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.03em;
		padding: 0.2rem 0.55rem;
		border-radius: 999px;
		background: #e5e7eb;
		color: #374151;
	}

	.status.active {
		background: #dcfce7;
		color: #166534;
	}

	.status.suspended {
		background: #fef9c3;
		color: #854d0e;
	}

	.status.terminated {
		background: #fee2e2;
		color: #991b1b;
	}

	.gauges {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.no-quota {
		margin: 0;
		color: #6b7280;
		font-size: 0.85rem;
	}
</style>
