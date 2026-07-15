<script lang="ts">
	// Post-login dashboard summary (16.5.3): a compact, read-only overview of the
	// caller's account from a single GET /bff/v1/home - profile, active-subscription
	// count with a short list, and the latest invoice with its paid/overdue tone.
	// It is a SUMMARY with links onward: "View subscriptions" -> /account and
	// "View invoices" -> /invoices. It deliberately does NOT reproduce the full
	// account/invoices views. The display shaping lives in the pure, unit-tested
	// $lib/home/summary module; this component only renders it.
	import type { HomeDashboard } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';
	import { invoiceStatusTone, summarizeHome } from './summary';

	let { home }: { home: HomeDashboard } = $props();

	const summary = $derived(summarizeHome(home));
</script>

<div class="dashboard">
	<section class="card profile">
		<h2>Profile</h2>
		<span class="name">{summary.customerName}</span>
		<span class="meta">Customer {summary.customerId}</span>
		<span class="meta">Status: {summary.accountStatus}</span>
	</section>

	<section class="card subscriptions">
		<div class="card-head">
			<h2>Subscriptions</h2>
			<span class="count">{summary.activeSubscriptionCount} active</span>
		</div>
		{#if summary.hasActiveSubscriptions}
			<ul class="sub-list">
				{#each home.activeSubscriptions as sub (sub.subscriptionId)}
					<li>
						<span class="msisdn">{sub.msisdn}</span>
						<span class="tariff">{sub.tariffCode}</span>
						<span class="sub-status">{sub.status}</span>
					</li>
				{/each}
			</ul>
		{:else}
			<p class="hint">No active subscriptions yet.</p>
		{/if}
		<a class="onward" href="/account">View subscriptions and usage</a>
	</section>

	<section class="card invoice">
		<h2>Latest invoice</h2>
		{#if summary.latestInvoice}
			{@const inv = summary.latestInvoice}
			<div class="invoice-row">
				<span class="period">{inv.period}</span>
				<span class="amount">{formatMoney(inv.amount, inv.currency)}</span>
				<span class={`status ${invoiceStatusTone(inv.status)}`}>{inv.status}</span>
			</div>
		{:else}
			<p class="hint">No invoices yet.</p>
		{/if}
		<a class="onward" href="/invoices">View all invoices</a>
	</section>
</div>

<style>
	.dashboard {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
		gap: 1rem;
	}

	.card {
		display: flex;
		flex-direction: column;
		gap: 0.4rem;
		background: #ffffff;
		border: 1px solid #e5e7eb;
		border-radius: 0.75rem;
		padding: 1.25rem;
	}

	h2 {
		margin: 0;
		font-size: 1rem;
		color: #374151;
	}

	.card-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 0.75rem;
	}

	.count {
		font-size: 0.8rem;
		color: #6b7280;
	}

	.profile .name {
		font-weight: 600;
		font-size: 1.15rem;
	}

	.meta {
		color: #6b7280;
		font-size: 0.85rem;
	}

	.sub-list {
		list-style: none;
		margin: 0.25rem 0 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.sub-list li {
		display: flex;
		align-items: center;
		gap: 0.6rem;
		font-size: 0.9rem;
	}

	.msisdn {
		font-variant-numeric: tabular-nums;
		font-weight: 600;
	}

	.tariff {
		color: #4b5563;
	}

	.sub-status {
		margin-left: auto;
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.03em;
		color: #6b7280;
	}

	.invoice-row {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		font-size: 0.95rem;
	}

	.period {
		color: #4b5563;
	}

	.amount {
		font-variant-numeric: tabular-nums;
		font-weight: 600;
	}

	.status {
		margin-left: auto;
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.03em;
		padding: 0.15rem 0.5rem;
		border-radius: 999px;
		background: #e5e7eb;
		color: #374151;
	}

	.status.paid {
		background: #dcfce7;
		color: #166534;
	}

	.status.overdue {
		background: #fee2e2;
		color: #991b1b;
	}

	.status.pending {
		background: #fef9c3;
		color: #854d0e;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.onward {
		margin-top: 0.4rem;
		font-size: 0.85rem;
		color: #16213e;
		font-weight: 600;
		text-decoration: none;
	}

	.onward:hover {
		text-decoration: underline;
	}
</style>
