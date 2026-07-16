<script lang="ts">
	// CRM orders: look up an order by id and inspect it, with a cancel action for
	// orders still in a cancellable state. ADMIN-only in this console. A `?id=` (e.g.
	// from the customer 360) loads immediately.
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { ApiError, api, type Order } from '$lib/api/client';
	import { isCancellable } from '$lib/orders/cancellable';
	import RoleGate from '$lib/crm/RoleGate.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Modal from '$lib/ui/Modal.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { orderTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const ALLOW = ['ADMIN'];
	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);

	let lookupId = $state('');
	let order = $state<Order | null>(null);
	let loading = $state(false);
	let error = $state('');
	let notFound = $state(false);

	let cancelOpen = $state(false);
	let cancelReason = $state('');
	let cancelling = $state(false);

	onMount(() => {
		const prefill = $page.url.searchParams.get('id');
		if (prefill) {
			lookupId = prefill;
			void lookup();
		}
	});

	async function lookup() {
		const id = lookupId.trim();
		if (!id) return;
		loading = true;
		error = '';
		notFound = false;
		order = null;
		try {
			order = await api.getOrder(id);
		} catch (err) {
			if (err instanceof ApiError && (err.status === 404 || err.status === 403)) notFound = true;
			else
				error = err instanceof ApiError ? `Lookup failed. (HTTP ${err.status})` : 'Lookup failed.';
		} finally {
			loading = false;
		}
	}

	async function confirmCancel() {
		if (!order) return;
		cancelling = true;
		try {
			await api.cancelOrder(order.id, cancelReason.trim() || undefined);
			toasts.success('Order cancelled.');
			cancelOpen = false;
			await lookup();
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? (err.serverMessage ?? `Could not cancel. (HTTP ${err.status})`)
					: 'Could not cancel.'
			);
		} finally {
			cancelling = false;
		}
	}

	function fmtMoney(amount: number): string {
		return amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
	}

	function fmtDateTime(iso: string): string {
		return new Date(iso).toLocaleString();
	}
</script>

<section class="page">
	<PageHeader title="Orders" subtitle="Inspect and cancel an order." />

	<RoleGate {roles} allow={ALLOW}>
		{#snippet children()}
			<Card>
				<div class="lookup">
					<Input
						id="order-lookup"
						label="Order id"
						bind:value={lookupId}
						placeholder="UUID"
						hint="Find an order id via Customers -> open a customer -> Orders."
					/>
					<Button variant="secondary" size="sm" {loading} onclick={lookup}>Look up</Button>
				</div>
			</Card>

			{#if error}
				<Alert tone="danger">{#snippet children()}<p>{error}</p>{/snippet}</Alert>
			{:else if notFound}
				<Alert tone="warning"
					>{#snippet children()}<p>No order was found for that id.</p>{/snippet}</Alert
				>
			{:else if order}
				{@const o = order}
				<Card>
					<div class="head">
						<div>
							<h2 class="mono">{o.id}</h2>
							<span class="muted mono">Customer {o.customerId}</span>
						</div>
						<Badge tone={orderTone(o.status)}>{#snippet children()}{o.status}{/snippet}</Badge>
					</div>
					<div class="meta">
						<div class="field">
							<span class="k">Placed</span><span class="v">{fmtDateTime(o.createdAt)}</span>
						</div>
						<div class="field">
							<span class="k">Updated</span><span class="v">{fmtDateTime(o.updatedAt)}</span>
						</div>
						<div class="field">
							<span class="k">Total</span><span class="v tabular">{fmtMoney(o.totalAmount)}</span>
						</div>
						<div class="field">
							<span class="k">Items</span><span class="v">{o.items.length}</span>
						</div>
					</div>
					{#if isCancellable(o.status)}
						<div class="actions">
							<Button variant="danger" size="sm" onclick={() => (cancelOpen = true)}
								>Cancel order</Button
							>
						</div>
					{/if}
				</Card>
			{/if}
		{/snippet}
	</RoleGate>
</section>

<Modal bind:open={cancelOpen} title="Cancel order">
	{#snippet children()}
		<p class="confirm">Only orders not yet fulfilled can be cancelled. This cannot be undone.</p>
		<Textarea id="crm-cancel-reason" label="Reason (optional)" bind:value={cancelReason} rows={2} />
	{/snippet}
	{#snippet footer()}
		<Button variant="secondary" size="sm" onclick={() => (cancelOpen = false)}>Keep order</Button>
		<Button variant="danger" size="sm" loading={cancelling} onclick={confirmCancel}
			>Cancel order</Button
		>
	{/snippet}
</Modal>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.lookup {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: end;
		gap: var(--space-3);
		max-width: 34rem;
	}

	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-4);
		margin-bottom: var(--space-5);
	}

	.head h2 {
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
		overflow-wrap: anywhere;
	}

	.muted {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.actions {
		display: flex;
		justify-content: flex-end;
		margin-top: var(--space-5);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.confirm {
		margin-bottom: var(--space-4);
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}
</style>
