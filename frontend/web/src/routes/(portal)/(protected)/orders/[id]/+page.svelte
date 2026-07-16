<script lang="ts">
	// Order detail: the full order (items, totals, status) plus its payment status
	// when one exists. Charging is event-driven, so an order may legitimately have no
	// payment yet - a 403/404 from payment-service is treated as "no payment
	// information", not an error. A cancellable order can be cancelled here too.
	import { onMount } from 'svelte';
	import { page as pageStore } from '$app/stores';
	import { ApiError, api, type Order, type Payment } from '$lib/api/client';
	import { isCancellable } from '$lib/orders/cancellable';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Modal from '$lib/ui/Modal.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { orderTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const orderId = $derived($pageStore.params.id ?? '');

	let order = $state<Order | null>(null);
	let payment = $state<Payment | null>(null);
	let loading = $state(true);
	let error = $state('');

	let cancelOpen = $state(false);
	let cancelReason = $state('');
	let cancelling = $state(false);

	onMount(() => {
		void load();
	});

	async function load() {
		loading = true;
		error = '';
		try {
			order = await api.getOrder(orderId);
			payment = await api.getPaymentByOrder(orderId).catch(() => null);
		} catch (err) {
			error =
				err instanceof ApiError
					? `Could not load the order. (HTTP ${err.status})`
					: 'Could not load the order.';
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
			await load();
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? (err.serverMessage ?? `Could not cancel the order. (HTTP ${err.status})`)
					: 'Could not cancel the order.'
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
	<PageHeader title="Order detail" subtitle="Items, totals, and payment status.">
		{#snippet actions()}
			<Button variant="secondary" size="sm" href="/orders">Back to orders</Button>
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
	{:else if order}
		{@const o = order}
		<Card>
			<div class="summary">
				<div class="field">
					<span class="k">Order</span>
					<span class="v mono">{o.id}</span>
				</div>
				<div class="field">
					<span class="k">Status</span>
					<Badge tone={orderTone(o.status)}>{#snippet children()}{o.status}{/snippet}</Badge>
				</div>
				<div class="field">
					<span class="k">Placed</span>
					<span class="v">{fmtDateTime(o.createdAt)}</span>
				</div>
				<div class="field">
					<span class="k">Total</span>
					<span class="v tabular">{fmtMoney(o.totalAmount)}</span>
				</div>
			</div>

			{#if isCancellable(o.status)}
				<div class="order-actions">
					<Button variant="danger" size="sm" onclick={() => (cancelOpen = true)}
						>Cancel order</Button
					>
				</div>
			{/if}
		</Card>

		<Card>
			<h2>Items</h2>
			<div class="table-scroll">
				<table>
					<thead>
						<tr>
							<th scope="col">Tariff</th>
							<th scope="col" class="right">Unit price</th>
							<th scope="col" class="right">Qty</th>
						</tr>
					</thead>
					<tbody>
						{#each o.items as item (item.id)}
							<tr>
								<td>{item.tariffName} <span class="muted">({item.tariffCode})</span></td>
								<td class="right tabular">{fmtMoney(item.unitPrice)}</td>
								<td class="right">{item.quantity}</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</Card>

		<Card>
			<h2>Payment</h2>
			{#if payment}
				{@const p = payment}
				<div class="summary">
					<div class="field">
						<span class="k">Status</span>
						<Badge tone={orderTone(p.status)}>{#snippet children()}{p.status}{/snippet}</Badge>
					</div>
					<div class="field">
						<span class="k">Amount</span>
						<span class="v tabular">{fmtMoney(p.amount)}</span>
					</div>
					<div class="field">
						<span class="k">Attempts</span>
						<span class="v">{p.attemptCount}</span>
					</div>
				</div>
			{:else}
				<p class="muted">
					No payment has been recorded for this order yet. Charging happens automatically once the
					order is confirmed.
				</p>
			{/if}
		</Card>
	{/if}
</section>

<Modal bind:open={cancelOpen} title="Cancel order">
	{#snippet children()}
		<p class="confirm">This cannot be undone. Only orders not yet fulfilled can be cancelled.</p>
		<Textarea
			id="cancel-reason-detail"
			label="Reason (optional)"
			bind:value={cancelReason}
			rows={2}
			placeholder="Tell us why you are cancelling"
		/>
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
		max-width: 52rem;
	}

	.summary {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
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

	.order-actions {
		display: flex;
		justify-content: flex-end;
		margin-top: var(--space-5);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	h2 {
		margin-bottom: var(--space-4);
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.table-scroll {
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: collapse;
	}

	th {
		text-align: left;
		padding: var(--space-2) var(--space-3);
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
		border-bottom: 1px solid var(--color-border);
	}

	th.right,
	td.right {
		text-align: right;
	}

	td {
		padding: var(--space-2) var(--space-3);
		font-size: var(--text-sm-size);
		border-bottom: 1px solid var(--color-border);
	}

	tbody tr:last-child td {
		border-bottom: 0;
	}

	.muted {
		color: var(--color-text-muted);
	}

	.confirm {
		margin-bottom: var(--space-4);
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}
</style>
