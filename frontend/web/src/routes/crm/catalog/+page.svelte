<script lang="ts">
	// CRM catalog: browse the tariff and add-on catalog. Any authenticated staff user
	// may read it (product-catalog-service leaves reads open). Admin catalog writes
	// exist but are out of scope for this console, so this view is read-only.
	import { onMount } from 'svelte';
	import { ApiError, api, type CatalogAddon, type CatalogTariff } from '$lib/api/client';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Table from '$lib/ui/Table.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';

	const TABS = [
		{ id: 'tariffs', label: 'Tariffs' },
		{ id: 'addons', label: 'Add-ons' }
	];
	let active = $state('tariffs');

	let tariffs = $state<CatalogTariff[]>([]);
	let addons = $state<CatalogAddon[]>([]);
	let loading = $state(true);
	let error = $state('');

	const tariffColumns = [
		{ key: 'code', label: 'Code', sortable: true },
		{ key: 'name', label: 'Name', sortable: true },
		{ key: 'monthlyFee', label: 'Monthly', align: 'right' as const, sortable: true },
		{ key: 'minutesIncluded', label: 'Minutes', align: 'right' as const },
		{ key: 'dataMbIncluded', label: 'Data (MB)', align: 'right' as const },
		{ key: 'status', label: 'Status' }
	];
	const addonColumns = [
		{ key: 'code', label: 'Code', sortable: true },
		{ key: 'name', label: 'Name', sortable: true },
		{ key: 'price', label: 'Price', align: 'right' as const, sortable: true },
		{ key: 'validityDays', label: 'Validity', align: 'right' as const },
		{ key: 'status', label: 'Status' }
	];

	onMount(() => {
		void load();
	});

	async function load() {
		loading = true;
		error = '';
		try {
			const [t, a] = await Promise.all([
				api.listTariffs(0, 100),
				api.listAddons(undefined, 0, 100)
			]);
			tariffs = t.content;
			addons = a.content;
		} catch (err) {
			error =
				err instanceof ApiError
					? `Could not load the catalog. (HTTP ${err.status})`
					: 'Could not load the catalog.';
		} finally {
			loading = false;
		}
	}

	function fmtMoney(amount: number, currency: string): string {
		return `${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
	}
</script>

<section class="page">
	<PageHeader title="Catalog" subtitle="Tariffs and add-ons offered on the platform." />

	{#if error}
		<Alert tone="danger">
			{#snippet children()}<p>{error}</p>{/snippet}
			{#snippet actions()}<Button variant="secondary" size="sm" onclick={load}>Retry</Button
				>{/snippet}
		</Alert>
	{:else}
		<Tabs tabs={TABS} bind:active>
			{#snippet panel(id)}
				{#if id === 'tariffs'}
					<Card padding="none">
						<Table columns={tariffColumns} rows={tariffs} rowKey={(t) => t.id} {loading}>
							{#snippet cell(tariff, key)}
								{#if key === 'code'}
									<span class="mono">{tariff.code}</span>
								{:else if key === 'name'}
									{tariff.name}
								{:else if key === 'monthlyFee'}
									<span class="tabular">{fmtMoney(tariff.monthlyFee, tariff.currency)}</span>
								{:else if key === 'minutesIncluded'}
									<span class="tabular">{tariff.minutesIncluded.toLocaleString()}</span>
								{:else if key === 'dataMbIncluded'}
									<span class="tabular">{tariff.dataMbIncluded.toLocaleString()}</span>
								{:else if key === 'status'}
									<Badge tone={tariff.status === 'ACTIVE' ? 'success' : 'neutral'}>
										{#snippet children()}{tariff.status}{/snippet}
									</Badge>
								{/if}
							{/snippet}
							{#snippet empty()}
								<EmptyState title="No tariffs" message="No tariffs are published." />
							{/snippet}
						</Table>
					</Card>
				{:else if id === 'addons'}
					<Card padding="none">
						<Table columns={addonColumns} rows={addons} rowKey={(a) => a.id} {loading}>
							{#snippet cell(addon, key)}
								{#if key === 'code'}
									<span class="mono">{addon.code}</span>
								{:else if key === 'name'}
									{addon.name}
								{:else if key === 'price'}
									<span class="tabular">{fmtMoney(addon.price, addon.currency)}</span>
								{:else if key === 'validityDays'}
									<span class="tabular">{addon.validityDays} days</span>
								{:else if key === 'status'}
									<Badge tone={addon.status === 'ACTIVE' ? 'success' : 'neutral'}>
										{#snippet children()}{addon.status}{/snippet}
									</Badge>
								{/if}
							{/snippet}
							{#snippet empty()}
								<EmptyState title="No add-ons" message="No add-ons are published." />
							{/snippet}
						</Table>
					</Card>
				{/if}
			{/snippet}
		</Tabs>
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-xs-size);
		color: var(--color-text-secondary);
	}
</style>
