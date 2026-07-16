<script lang="ts">
	// CRM dashboard. Shows the one KPI it can source honestly - the total customer
	// count, for staff who may read the customer list - and marks the metrics that
	// have no endpoint yet as "Planned" rather than inventing numbers. Below the KPIs
	// is a set of quick links into the wired console sections.
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { api } from '$lib/api/client';
	import Card from '$lib/ui/Card.svelte';
	import ComingSoon from '$lib/ui/ComingSoon.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import StatCard from '$lib/ui/StatCard.svelte';

	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);
	const canReadCustomers = $derived(
		['ADMIN', 'CALL_CENTER_AGENT'].some((role) => roles.includes(role))
	);

	let customerCount = $state<string | null>(null);

	const quickLinks = [
		{ href: '/crm/customers', label: 'Customers', desc: 'Search and open customer records' },
		{ href: '/crm/subscriptions', label: 'Subscriptions', desc: 'Look up and manage a line' },
		{ href: '/crm/orders', label: 'Orders', desc: 'Inspect and cancel orders' },
		{ href: '/crm/billing', label: 'Billing', desc: 'Find invoices and download PDFs' },
		{ href: '/crm/tickets', label: 'Tickets', desc: 'Assign and resolve support tickets' },
		{ href: '/crm/catalog', label: 'Catalog', desc: 'Browse tariffs and add-ons' }
	];

	onMount(() => {
		if (canReadCustomers) void loadCount();
	});

	async function loadCount() {
		try {
			const firstPage = await api.listCustomers(0, 1);
			customerCount = firstPage.totalElements.toLocaleString();
		} catch {
			customerCount = '—';
		}
	}
</script>

<section class="page">
	<PageHeader title="Dashboard" subtitle="Operational overview of the Telco CRM platform." />

	<div class="kpis">
		{#if canReadCustomers}
			<StatCard label="Customers" value={customerCount} hint="Total records" tone="info" />
		{:else}
			<StatCard label="Customers" value="—" hint="Requires staff role" />
		{/if}
		<StatCard label="Active subscriptions" value="—" hint="Planned metric" />
		<StatCard label="Open tickets" value="—" hint="Planned metric" />
		<StatCard label="Monthly revenue" value="—" hint="Planned metric" />
	</div>

	<Card>
		<h2>Quick links</h2>
		<div class="links">
			{#each quickLinks as link (link.href)}
				<a class="link" href={link.href}>
					<span class="link-label">{link.label}</span>
					<span class="link-desc">{link.desc}</span>
				</a>
			{/each}
		</div>
	</Card>

	<ComingSoon
		title="Analytics & revenue dashboards"
		message="Charts for MRR, churn, activation funnel, and ticket SLAs are planned. They need reporting endpoints that the platform does not expose yet."
		cta="Request analytics"
	/>
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}

	.kpis {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
		gap: var(--space-4);
	}

	h2 {
		margin-bottom: var(--space-4);
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.links {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
		gap: var(--space-3);
	}

	.link {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		text-decoration: none;
		transition: border-color var(--duration-fast) var(--ease-out);
	}

	.link:hover {
		border-color: var(--color-border-strong);
		background: var(--color-surface-alt);
	}

	.link-label {
		font-size: var(--text-sm-size);
		font-weight: 700;
		color: var(--color-text);
	}

	.link-desc {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}
</style>
