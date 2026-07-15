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
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import { customerTone, invoiceToneToBadge, subscriptionTone } from '$lib/ui/status';

	let { home }: { home: HomeDashboard } = $props();

	const summary = $derived(summarizeHome(home));
</script>

<div class="dashboard">
	<Card>
		<div class="card-body">
			<div class="card-head">
				<span class="icon" aria-hidden="true">
					<svg viewBox="0 0 24 24">
						<circle cx="12" cy="8" r="3.6" />
						<path d="M5 20c0-3.6 3.1-5.6 7-5.6s7 2 7 5.6" />
					</svg>
				</span>
				<h2>Profile</h2>
			</div>

			<span class="headline">{summary.customerName}</span>
			<span class="meta mono">Customer {summary.customerId}</span>
			<div class="badge-row">
				<Badge tone={customerTone(summary.accountStatus)}>{summary.accountStatus}</Badge>
			</div>
		</div>
	</Card>

	<Card>
		<div class="card-body">
			<div class="card-head">
				<span class="icon" aria-hidden="true">
					<svg viewBox="0 0 24 24">
						<path d="M5 18v-4M10 18v-8M15 18v-11M20 18V5" />
					</svg>
				</span>
				<h2>Subscriptions</h2>
			</div>

			{#if summary.hasActiveSubscriptions}
				<p class="count">
					<span class="count-value tabular">{summary.activeSubscriptionCount}</span>
					<span class="count-label">active</span>
				</p>
				<ul class="sub-list">
					{#each home.activeSubscriptions as sub (sub.subscriptionId)}
						<li>
							<span class="msisdn mono">{sub.msisdn}</span>
							<span class="tariff">{sub.tariffCode}</span>
							<Badge tone={subscriptionTone(sub.status)}>{sub.status}</Badge>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="hint">No active subscriptions yet.</p>
			{/if}

			<div class="onward">
				<Button variant="ghost" size="sm" href="/account">View subscriptions and usage</Button>
			</div>
		</div>
	</Card>

	<Card>
		<div class="card-body">
			<div class="card-head">
				<span class="icon" aria-hidden="true">
					<svg viewBox="0 0 24 24">
						<path d="M6 3h9l3 3v15H6z" />
						<path d="M9 11h6M9 15h6" />
					</svg>
				</span>
				<h2>Latest invoice</h2>
			</div>

			{#if summary.latestInvoice}
				{@const inv = summary.latestInvoice}
				<span class="meta">{inv.period}</span>
				<span class="amount tabular">{formatMoney(inv.amount, inv.currency)}</span>
				<div class="badge-row">
					<Badge tone={invoiceToneToBadge(invoiceStatusTone(inv.status))}>{inv.status}</Badge>
				</div>
			{:else}
				<p class="hint">No invoices yet.</p>
			{/if}

			<div class="onward">
				<Button variant="ghost" size="sm" href="/invoices">View all invoices</Button>
			</div>
		</div>
	</Card>
</div>

<style>
	.dashboard {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
		gap: var(--space-4);
	}

	.card-body {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		height: 100%;
	}

	.card-head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-bottom: var(--space-2);
	}

	.icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2.5rem;
		height: 2.5rem;
		flex-shrink: 0;
		border-radius: var(--radius-full);
		background: var(--color-accent-soft);
		color: var(--color-on-accent-soft);
	}

	.icon svg {
		width: 1.25rem;
		height: 1.25rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 1.8;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	h2 {
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}

	.headline {
		font-size: var(--text-xl-size);
		line-height: var(--text-xl-lh);
		font-weight: 600;
	}

	.amount {
		font-size: var(--text-2xl-size);
		line-height: var(--text-2xl-lh);
		font-weight: 700;
	}

	.meta {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	.mono {
		font-family: var(--font-mono);
	}

	.count {
		display: flex;
		align-items: baseline;
		gap: var(--space-2);
	}

	.count-value {
		font-size: var(--text-3xl-size);
		line-height: var(--text-3xl-lh);
		font-weight: 700;
	}

	.count-label {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	.badge-row {
		margin-top: var(--space-1);
	}

	.sub-list {
		list-style: none;
		margin: var(--space-2) 0 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.sub-list li {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--text-sm-size);
	}

	.msisdn {
		font-weight: 600;
	}

	.tariff {
		color: var(--color-text-muted);
	}

	.sub-list li :global(.badge) {
		margin-left: auto;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	/* Pins the onward link to the bottom so the three cards' footers line up even
	   when their bodies differ in height. */
	.onward {
		margin-top: auto;
		padding-top: var(--space-3);
		margin-left: calc(-1 * var(--space-3));
	}
</style>
