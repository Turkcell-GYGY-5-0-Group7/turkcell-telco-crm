<script lang="ts">
	// Orders view: the caller's own orders (order-service, via the gateway thin-slice
	// GET /orders/customer/{id}, which the service filters to the caller for a
	// non-admin). A PENDING/CONFIRMED order can be cancelled here; the confirm dialog
	// issues the DELETE and, if the server refuses the transition (the local view was
	// stale), surfaces the exact business-rule message rather than a generic error.
	import { onMount } from 'svelte';
	import { ApiError, api, type Order, type PageResult } from '$lib/api/client';
	import { renewSession } from '$lib/auth/oidc';
	import { currentCustomerId } from '$lib/auth/session';
	import { isCancellable } from '$lib/orders/cancellable';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Modal from '$lib/ui/Modal.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Pagination from '$lib/ui/Pagination.svelte';
	import Table from '$lib/ui/Table.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { orderTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const PAGE_SIZE = 10;

	let result = $state<PageResult<Order> | null>(null);
	let page = $state(0);
	let loading = $state(true);
	let error = $state('');
	let notOnboarded = $state(false);

	let cancelTarget = $state<Order | null>(null);
	let cancelReason = $state('');
	let cancelling = $state(false);
	let cancelOpen = $state(false);

	const columns = [
		{ key: 'id', label: 'Order' },
		{ key: 'createdAt', label: 'Placed' },
		{ key: 'items', label: 'Items' },
		{ key: 'totalAmount', label: 'Total', align: 'right' as const },
		{ key: 'status', label: 'Status' },
		{ key: 'actions', label: '', align: 'right' as const }
	];

	onMount(() => {
		void load(0);
	});

	async function load(target: number) {
		loading = true;
		error = '';
		notOnboarded = false;
		const customerId = currentCustomerId();
		if (!customerId) {
			notOnboarded = true;
			loading = false;
			return;
		}
		try {
			const data = await api.listMyOrders(customerId, target, PAGE_SIZE);
			result = data;
			page = data.page;
		} catch (err) {
			// An unlinked token can still 403 here if the claim lags; try one renew.
			if (err instanceof ApiError && err.status === 403 && (await renewSession())) {
				return load(target);
			}
			error =
				err instanceof ApiError
					? `Could not load your orders. (HTTP ${err.status})`
					: 'Could not load your orders.';
		} finally {
			loading = false;
		}
	}

	function openCancel(order: Order) {
		cancelTarget = order;
		cancelReason = '';
		cancelOpen = true;
	}

	async function confirmCancel() {
		if (!cancelTarget) return;
		cancelling = true;
		try {
			await api.cancelOrder(cancelTarget.id, cancelReason.trim() || undefined);
			toasts.success('Order cancelled.');
			cancelOpen = false;
			cancelTarget = null;
			await load(page);
		} catch (err) {
			const message =
				err instanceof ApiError
					? (err.serverMessage ?? `Could not cancel the order. (HTTP ${err.status})`)
					: 'Could not cancel the order.';
			toasts.error(message);
		} finally {
			cancelling = false;
		}
	}

	function shortId(id: string): string {
		return id.slice(0, 8);
	}

	function fmtDate(iso: string): string {
		return new Date(iso).toLocaleDateString();
	}

	function fmtMoney(amount: number): string {
		return amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
	}
</script>

<section class="page">
	<PageHeader title="Orders" subtitle="Orders you have placed and their status." />

	{#if loading}
		<Card padding="none">
			<Table {columns} rows={[] as Order[]} rowKey={(o) => o.id} loading>
				{#snippet cell()}{/snippet}
			</Table>
		</Card>
	{:else if error}
		<Alert tone="danger">
			{#snippet children()}<p>{error}</p>{/snippet}
			{#snippet actions()}<Button variant="secondary" size="sm" onclick={() => load(page)}
					>Retry</Button
				>{/snippet}
		</Alert>
	{:else if notOnboarded}
		<NotOnboardedNotice
			message="You have not placed any orders yet. Once you start onboarding, the order that activates your line will be tracked here."
		/>
	{:else if result}
		<Card padding="none">
			<Table {columns} rows={result.content} rowKey={(o) => o.id}>
				{#snippet cell(order, key)}
					{#if key === 'id'}
						<a class="link" href={`/orders/${order.id}`}>{shortId(order.id)}</a>
					{:else if key === 'createdAt'}
						{fmtDate(order.createdAt)}
					{:else if key === 'items'}
						{order.items.length}
						{order.items.length === 1 ? 'item' : 'items'}
					{:else if key === 'totalAmount'}
						<span class="tabular">{fmtMoney(order.totalAmount)}</span>
					{:else if key === 'status'}
						<Badge tone={orderTone(order.status)}
							>{#snippet children()}{order.status}{/snippet}</Badge
						>
					{:else if key === 'actions'}
						<div class="row-actions">
							<Button variant="ghost" size="sm" href={`/orders/${order.id}`}>View</Button>
							{#if isCancellable(order.status)}
								<Button variant="danger" size="sm" onclick={() => openCancel(order)}>Cancel</Button>
							{/if}
						</div>
					{/if}
				{/snippet}
				{#snippet empty()}
					<EmptyState
						title="No orders yet"
						message="Orders you place will be listed here with their live status."
					>
						{#snippet action()}<Button href="/onboarding">Start onboarding</Button>{/snippet}
					</EmptyState>
				{/snippet}
			</Table>
		</Card>

		{#if result.totalPages > 1}
			<Pagination
				{page}
				totalPages={result.totalPages}
				disabled={loading}
				onPrev={() => load(page - 1)}
				onNext={() => load(page + 1)}
			/>
		{/if}
	{/if}
</section>

<Modal bind:open={cancelOpen} title="Cancel order">
	{#snippet children()}
		<p class="confirm">
			Cancel order <strong>{cancelTarget ? shortId(cancelTarget.id) : ''}</strong>? This cannot be
			undone. Only orders that have not yet been fulfilled can be cancelled.
		</p>
		<Textarea
			id="cancel-reason"
			label="Reason (optional)"
			bind:value={cancelReason}
			rows={2}
			placeholder="Tell us why you are cancelling"
		/>
	{/snippet}
	{#snippet footer()}
		<Button variant="secondary" size="sm" onclick={() => (cancelOpen = false)}>Keep order</Button>
		<Button variant="danger" size="sm" loading={cancelling} onclick={confirmCancel}>
			Cancel order
		</Button>
	{/snippet}
</Modal>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 60rem;
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

	.row-actions {
		display: inline-flex;
		gap: var(--space-2);
		justify-content: flex-end;
	}

	.confirm {
		margin-bottom: var(--space-4);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
		color: var(--color-text-secondary);
	}
</style>
