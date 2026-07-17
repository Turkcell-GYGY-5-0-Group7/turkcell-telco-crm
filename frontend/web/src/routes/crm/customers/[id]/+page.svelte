<script lang="ts">
	// Customer 360: profile plus the customer's subscriptions, orders, and invoices on
	// tabs. Reading a customer is open to ADMIN / CALL_CENTER_AGENT; the cross-service
	// sub-resources (subscriptions/orders/invoices by customerId) are ADMIN-only in
	// this console, so a non-admin agent sees a role notice on those tabs instead of a
	// guaranteed 403. Each tab lazy-loads on first view.
	import { onMount } from 'svelte';
	import { page as pageStore } from '$app/stores';
	import {
		ApiError,
		api,
		type Customer,
		type InvoiceDetail,
		type Order,
		type SubscriptionDetail
	} from '$lib/api/client';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Modal from '$lib/ui/Modal.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Table from '$lib/ui/Table.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';
	import { customerTone, orderTone, subscriptionTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const customerId = $derived($pageStore.params.id ?? '');
	const roles = $derived(($pageStore.data.roles as string[] | undefined) ?? []);
	const isAdmin = $derived(roles.includes('ADMIN'));

	const TABS = [
		{ id: 'profile', label: 'Profile' },
		{ id: 'subscriptions', label: 'Subscriptions' },
		{ id: 'orders', label: 'Orders' },
		{ id: 'invoices', label: 'Invoices' }
	];
	let active = $state('profile');

	let customer = $state<Customer | null>(null);
	let loading = $state(true);
	let error = $state('');

	let subs = $state<SubscriptionDetail[] | null>(null);
	let orders = $state<Order[] | null>(null);
	let invoices = $state<InvoiceDetail[] | null>(null);
	let subError = $state('');
	let orderError = $state('');
	let invoiceError = $state('');

	let editOpen = $state(false);
	let saving = $state(false);
	let firstName = $state('');
	let lastName = $state('');
	let dateOfBirth = $state('');
	let editError = $state('');

	// Lazy-load a tab's data the first time it becomes active (ADMIN only).
	$effect(() => {
		if (!isAdmin || !customer) return;
		if (active === 'subscriptions' && subs === null) void loadSubs();
		if (active === 'orders' && orders === null) void loadOrders();
		if (active === 'invoices' && invoices === null) void loadInvoices();
	});

	onMount(() => {
		void load();
	});

	async function load() {
		loading = true;
		error = '';
		try {
			customer = await api.getCustomer(customerId);
		} catch (err) {
			error =
				err instanceof ApiError
					? `Could not load the customer. (HTTP ${err.status})`
					: 'Could not load the customer.';
		} finally {
			loading = false;
		}
	}

	async function loadSubs() {
		subError = '';
		try {
			subs = (await api.listSubscriptions(customerId, 0, 50)).content;
		} catch (err) {
			subError = err instanceof ApiError ? `HTTP ${err.status}` : 'error';
			subs = [];
		}
	}

	async function loadOrders() {
		orderError = '';
		try {
			orders = (await api.listMyOrders(customerId, 0, 50)).content;
		} catch (err) {
			orderError = err instanceof ApiError ? `HTTP ${err.status}` : 'error';
			orders = [];
		}
	}

	async function loadInvoices() {
		invoiceError = '';
		try {
			invoices = (await api.listInvoicesForCustomer(customerId, 0, 50)).content;
		} catch (err) {
			invoiceError = err instanceof ApiError ? `HTTP ${err.status}` : 'error';
			invoices = [];
		}
	}

	function openEdit() {
		if (!customer) return;
		firstName = customer.firstName;
		lastName = customer.lastName;
		dateOfBirth = customer.dateOfBirth;
		editError = '';
		editOpen = true;
	}

	async function saveProfile() {
		editError = '';
		if (firstName.trim().length === 0 || lastName.trim().length === 0) {
			editError = 'First and last name are required.';
			return;
		}
		saving = true;
		try {
			customer = await api.updateCustomer(customerId, {
				firstName: firstName.trim(),
				lastName: lastName.trim(),
				dateOfBirth
			});
			toasts.success('Customer updated.');
			editOpen = false;
		} catch (err) {
			editError =
				err instanceof ApiError
					? (err.serverMessage ?? `Could not save. (HTTP ${err.status})`)
					: 'Could not save.';
		} finally {
			saving = false;
		}
	}

	function fmtDate(iso: string): string {
		return new Date(iso).toLocaleDateString();
	}

	function fmtMoney(amount: number, currency?: string): string {
		const value = amount.toLocaleString('en-US', {
			minimumFractionDigits: 2,
			maximumFractionDigits: 2
		});
		return currency ? `${value} ${currency}` : value;
	}
</script>

<section class="page">
	<PageHeader title="Customer" subtitle="Full record across the platform.">
		{#snippet actions()}
			<Button variant="secondary" size="sm" href="/crm/customers">Back to customers</Button>
		{/snippet}
	</PageHeader>

	{#if loading}
		<Card><Skeleton variant="text" lines={4} /></Card>
	{:else if error}
		<Alert tone="danger">
			{#snippet children()}<p>{error}</p>{/snippet}
			{#snippet actions()}<Button variant="secondary" size="sm" onclick={load}>Retry</Button
				>{/snippet}
		</Alert>
	{:else if customer}
		{@const c = customer}
		<Tabs tabs={TABS} bind:active>
			{#snippet panel(id)}
				{#if id === 'profile'}
					<Card>
						<div class="profile-head">
							<div>
								<h2>{c.firstName} {c.lastName}</h2>
								<span class="mono">{c.id}</span>
							</div>
							<Badge tone={customerTone(c.status)}>{#snippet children()}{c.status}{/snippet}</Badge>
						</div>
						<div class="meta">
							<div class="field"><span class="k">Type</span><span class="v">{c.type}</span></div>
							<div class="field">
								<span class="k">Identity</span><span class="v mono">{c.identityNumberMasked}</span>
							</div>
							<div class="field">
								<span class="k">Date of birth</span><span class="v">{c.dateOfBirth}</span>
							</div>
							<div class="field">
								<span class="k">Joined</span><span class="v">{fmtDate(c.createdAt)}</span>
							</div>
						</div>
						<div class="profile-actions">
							<Button variant="secondary" size="sm" onclick={openEdit}>Edit profile</Button>
						</div>
					</Card>
				{:else if !isAdmin}
					<EmptyState
						title="Admin only"
						message="Subscriptions, orders, and invoices for a customer are available to ADMIN users in this console."
					/>
				{:else if id === 'subscriptions'}
					{#if subs === null}
						<Card><Skeleton variant="text" lines={3} /></Card>
					{:else if subError}
						<EmptyState
							title="Unavailable"
							message={`Could not load subscriptions (${subError}).`}
						/>
					{:else}
						<Card padding="none">
							<Table
								columns={[
									{ key: 'msisdn', label: 'MSISDN' },
									{ key: 'tariffCode', label: 'Tariff' },
									{ key: 'status', label: 'Status' },
									{ key: 'actions', label: '', align: 'right' }
								]}
								rows={subs}
								rowKey={(s) => s.id}
							>
								{#snippet cell(sub, key)}
									{#if key === 'msisdn'}
										<span class="mono">{sub.msisdn}</span>
									{:else if key === 'tariffCode'}
										{sub.tariffCode}
									{:else if key === 'status'}
										<Badge tone={subscriptionTone(sub.status)}
											>{#snippet children()}{sub.status}{/snippet}</Badge
										>
									{:else if key === 'actions'}
										<Button variant="ghost" size="sm" href={`/crm/subscriptions?id=${sub.id}`}
											>Manage</Button
										>
									{/if}
								{/snippet}
								{#snippet empty()}
									<EmptyState title="No subscriptions" message="This customer has no lines." />
								{/snippet}
							</Table>
						</Card>
					{/if}
				{:else if id === 'orders'}
					{#if orders === null}
						<Card><Skeleton variant="text" lines={3} /></Card>
					{:else if orderError}
						<EmptyState title="Unavailable" message={`Could not load orders (${orderError}).`} />
					{:else}
						<Card padding="none">
							<Table
								columns={[
									{ key: 'id', label: 'Order' },
									{ key: 'status', label: 'Status' },
									{ key: 'totalAmount', label: 'Total', align: 'right' },
									{ key: 'createdAt', label: 'Placed' }
								]}
								rows={orders}
								rowKey={(o) => o.id}
							>
								{#snippet cell(order, key)}
									{#if key === 'id'}
										<a class="link" href={`/crm/orders?id=${order.id}`}>{order.id.slice(0, 8)}</a>
									{:else if key === 'status'}
										<Badge tone={orderTone(order.status)}
											>{#snippet children()}{order.status}{/snippet}</Badge
										>
									{:else if key === 'totalAmount'}
										<span class="tabular">{fmtMoney(order.totalAmount)}</span>
									{:else if key === 'createdAt'}
										{fmtDate(order.createdAt)}
									{/if}
								{/snippet}
								{#snippet empty()}
									<EmptyState title="No orders" message="This customer has placed no orders." />
								{/snippet}
							</Table>
						</Card>
					{/if}
				{:else if id === 'invoices'}
					{#if invoices === null}
						<Card><Skeleton variant="text" lines={3} /></Card>
					{:else if invoiceError}
						<EmptyState
							title="Unavailable"
							message={`Could not load invoices (${invoiceError}).`}
						/>
					{:else}
						<Card padding="none">
							<Table
								columns={[
									{ key: 'id', label: 'Invoice' },
									{ key: 'grandTotal', label: 'Total', align: 'right' },
									{ key: 'status', label: 'Status' },
									{ key: 'dueDate', label: 'Due' }
								]}
								rows={invoices}
								rowKey={(inv) => inv.id}
							>
								{#snippet cell(invoice, key)}
									{#if key === 'id'}
										<a class="link" href={`/crm/billing?id=${invoice.id}`}
											>{invoice.id.slice(0, 8)}</a
										>
									{:else if key === 'grandTotal'}
										<span class="tabular">{fmtMoney(invoice.grandTotal, invoice.currency)}</span>
									{:else if key === 'status'}
										<Badge tone="neutral">{#snippet children()}{invoice.status}{/snippet}</Badge>
									{:else if key === 'dueDate'}
										{invoice.dueDate}
									{/if}
								{/snippet}
								{#snippet empty()}
									<EmptyState title="No invoices" message="This customer has no invoices yet." />
								{/snippet}
							</Table>
						</Card>
					{/if}
				{/if}
			{/snippet}
		</Tabs>
	{/if}
</section>

<Modal bind:open={editOpen} title="Edit customer">
	{#snippet children()}
		{#if editError}
			<div class="edit-error">
				<Alert tone="danger">{#snippet children()}<p>{editError}</p>{/snippet}</Alert>
			</div>
		{/if}
		<div class="edit-form">
			<Input id="c-first" label="First name" bind:value={firstName} required />
			<Input id="c-last" label="Last name" bind:value={lastName} required />
			<Input id="c-dob" label="Date of birth" type="date" bind:value={dateOfBirth} />
		</div>
	{/snippet}
	{#snippet footer()}
		<Button variant="secondary" size="sm" onclick={() => (editOpen = false)}>Cancel</Button>
		<Button size="sm" loading={saving} onclick={saveProfile}>Save changes</Button>
	{/snippet}
</Modal>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.profile-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-4);
		margin-bottom: var(--space-5);
	}

	.profile-head h2 {
		font-size: var(--text-lg-size);
		font-weight: 700;
	}

	.meta {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
		gap: var(--space-4);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.k {
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}

	.v {
		font-size: var(--text-sm-size);
	}

	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-xs-size);
		color: var(--color-text-secondary);
		overflow-wrap: anywhere;
	}

	.profile-actions {
		display: flex;
		justify-content: flex-end;
		margin-top: var(--space-5);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.link {
		font-family: var(--font-mono);
		color: var(--color-link);
		font-weight: 600;
		text-decoration: none;
	}

	.link:hover {
		text-decoration: underline;
	}

	.edit-form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.edit-error {
		margin-bottom: var(--space-4);
	}
</style>
