<script lang="ts">
	// CRM customers: a paged, sortable table of customer records. Restricted to ADMIN
	// / CALL_CENTER_AGENT (customer-service enforces this too); a user without the role
	// sees the RoleGate notice instead of a 403 wall. A row opens the customer-360.
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { ApiError, api, type Customer, type PageResult } from '$lib/api/client';
	import RoleGate from '$lib/crm/RoleGate.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Pagination from '$lib/ui/Pagination.svelte';
	import Table from '$lib/ui/Table.svelte';
	import { customerTone } from '$lib/ui/status';

	const ALLOW = ['ADMIN', 'CALL_CENTER_AGENT'];
	const PAGE_SIZE = 15;

	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);
	const permitted = $derived(ALLOW.some((role) => roles.includes(role)));

	let result = $state<PageResult<Customer> | null>(null);
	let pageIndex = $state(0);
	let loading = $state(true);
	let error = $state('');

	const columns = [
		{ key: 'firstName', label: 'Name', sortable: true },
		{ key: 'type', label: 'Type', sortable: true },
		{ key: 'identityNumberMasked', label: 'Identity' },
		{ key: 'status', label: 'Status', sortable: true },
		{ key: 'createdAt', label: 'Joined', sortable: true },
		{ key: 'actions', label: '', align: 'right' as const }
	];

	onMount(() => {
		if (permitted) void load(0);
		else loading = false;
	});

	async function load(target: number) {
		loading = true;
		error = '';
		try {
			const data = await api.listCustomers(target, PAGE_SIZE);
			result = data;
			pageIndex = data.page;
		} catch (err) {
			error =
				err instanceof ApiError
					? `Could not load customers. (HTTP ${err.status})`
					: 'Could not load customers.';
		} finally {
			loading = false;
		}
	}

	function fmtDate(iso: string): string {
		return new Date(iso).toLocaleDateString();
	}
</script>

<section class="page">
	<PageHeader title="Customers" subtitle="Search and open customer records." />

	<RoleGate {roles} allow={ALLOW}>
		{#snippet children()}
			{#if error}
				<Alert tone="danger">
					{#snippet children()}<p>{error}</p>{/snippet}
					{#snippet actions()}<Button variant="secondary" size="sm" onclick={() => load(pageIndex)}
							>Retry</Button
						>{/snippet}
				</Alert>
			{:else}
				<Card padding="none">
					<Table {columns} rows={result?.content ?? []} rowKey={(c) => c.id} {loading}>
						{#snippet cell(customer, key)}
							{#if key === 'firstName'}
								<a class="link" href={`/crm/customers/${customer.id}`}>
									{customer.firstName}
									{customer.lastName}
								</a>
							{:else if key === 'type'}
								{customer.type}
							{:else if key === 'identityNumberMasked'}
								<span class="mono">{customer.identityNumberMasked}</span>
							{:else if key === 'status'}
								<Badge tone={customerTone(customer.status)}
									>{#snippet children()}{customer.status}{/snippet}</Badge
								>
							{:else if key === 'createdAt'}
								{fmtDate(customer.createdAt)}
							{:else if key === 'actions'}
								<Button variant="ghost" size="sm" href={`/crm/customers/${customer.id}`}
									>Open</Button
								>
							{/if}
						{/snippet}
						{#snippet empty()}
							<EmptyState title="No customers" message="No customer records were found." />
						{/snippet}
					</Table>
				</Card>

				{#if result && result.totalPages > 1}
					<Pagination
						page={pageIndex}
						totalPages={result.totalPages}
						disabled={loading}
						onPrev={() => load(pageIndex - 1)}
						onNext={() => load(pageIndex + 1)}
					/>
				{/if}
			{/if}
		{/snippet}
	</RoleGate>
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.link {
		color: var(--color-link);
		font-weight: 600;
		text-decoration: none;
	}

	.link:hover {
		text-decoration: underline;
	}

	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-xs-size);
		color: var(--color-text-secondary);
	}
</style>
