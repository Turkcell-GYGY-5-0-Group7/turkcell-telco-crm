<script lang="ts">
	// CRM subscriptions: look up a subscription by id and drive its lifecycle
	// (suspend / reactivate / terminate). ADMIN-only in this console - the underlying
	// commands compare ownership against the caller's Keycloak subject, so a real
	// subscriber can only act on their own line, while ADMIN may act on any. A
	// prefilled `?id=` (e.g. from the customer 360) loads immediately.
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { ApiError, api, type SubscriptionDetail } from '$lib/api/client';
	import RoleGate from '$lib/crm/RoleGate.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Modal from '$lib/ui/Modal.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import { subscriptionTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const ALLOW = ['ADMIN'];
	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);

	let lookupId = $state('');
	let subscription = $state<SubscriptionDetail | null>(null);
	let loading = $state(false);
	let error = $state('');
	let notFound = $state(false);

	type Action = 'suspend' | 'reactivate' | 'terminate';
	let pendingAction = $state<Action | null>(null);
	let acting = $state(false);
	let confirmOpen = $state(false);

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
		subscription = null;
		try {
			subscription = await api.getSubscription(id);
		} catch (err) {
			if (err instanceof ApiError && (err.status === 404 || err.status === 403)) {
				notFound = true;
			} else {
				error = err instanceof ApiError ? `Lookup failed. (HTTP ${err.status})` : 'Lookup failed.';
			}
		} finally {
			loading = false;
		}
	}

	function ask(action: Action) {
		pendingAction = action;
		confirmOpen = true;
	}

	async function confirm() {
		if (!subscription || !pendingAction) return;
		acting = true;
		const id = subscription.id;
		try {
			if (pendingAction === 'suspend') await api.suspendSubscription(id);
			else if (pendingAction === 'reactivate') await api.reactivateSubscription(id);
			else await api.terminateSubscription(id);
			toasts.success(`Subscription ${pendingAction}d.`);
			confirmOpen = false;
			pendingAction = null;
			await lookup();
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? (err.serverMessage ?? `Action failed. (HTTP ${err.status})`)
					: 'Action failed.'
			);
		} finally {
			acting = false;
		}
	}

	function fmtDateTime(iso: string | null): string {
		return iso ? new Date(iso).toLocaleString() : '-';
	}
</script>

<section class="page">
	<PageHeader title="Subscriptions" subtitle="Look up a line and manage its lifecycle." />

	<RoleGate {roles} allow={ALLOW}>
		{#snippet children()}
			<Card>
				<div class="lookup">
					<Input
						id="sub-lookup"
						label="Subscription id"
						bind:value={lookupId}
						placeholder="UUID"
						hint="Not the customer id. Find a subscription id via Customers -> open a customer -> Subscriptions -> Manage."
					/>
					<Button variant="secondary" size="sm" {loading} onclick={lookup}>Look up</Button>
				</div>
			</Card>

			{#if error}
				<Alert tone="danger">{#snippet children()}<p>{error}</p>{/snippet}</Alert>
			{:else if notFound}
				<Alert tone="warning">
					{#snippet children()}<p>No subscription was found for that id.</p>{/snippet}
				</Alert>
			{:else if subscription}
				{@const s = subscription}
				<Card>
					<div class="head">
						<div>
							<h2 class="mono">{s.msisdn}</h2>
							<span class="mono muted">{s.id}</span>
						</div>
						<Badge tone={subscriptionTone(s.status)}
							>{#snippet children()}{s.status}{/snippet}</Badge
						>
					</div>
					<div class="meta">
						<div class="field">
							<span class="k">Tariff</span><span class="v">{s.tariffCode} v{s.tariffVersion}</span>
						</div>
						<div class="field">
							<span class="k">Activated</span><span class="v">{fmtDateTime(s.activatedAt)}</span>
						</div>
						<div class="field">
							<span class="k">Terminated</span><span class="v">{fmtDateTime(s.terminatedAt)}</span>
						</div>
						<div class="field">
							<span class="k">Created</span><span class="v">{fmtDateTime(s.createdAt)}</span>
						</div>
					</div>
					<div class="actions">
						<Button
							variant="secondary"
							size="sm"
							disabled={s.status !== 'ACTIVE'}
							onclick={() => ask('suspend')}
						>
							Suspend
						</Button>
						<Button
							variant="secondary"
							size="sm"
							disabled={s.status !== 'SUSPENDED'}
							onclick={() => ask('reactivate')}
						>
							Reactivate
						</Button>
						<Button
							variant="danger"
							size="sm"
							disabled={s.status === 'TERMINATED'}
							onclick={() => ask('terminate')}
						>
							Terminate
						</Button>
					</div>
				</Card>
			{/if}
		{/snippet}
	</RoleGate>
</section>

<Modal bind:open={confirmOpen} title={`Confirm ${pendingAction ?? ''}`} size="sm">
	{#snippet children()}
		<p class="confirm">
			{#if pendingAction === 'terminate'}
				Terminating a subscription is permanent. Continue?
			{:else}
				This will {pendingAction} the subscription. Continue?
			{/if}
		</p>
	{/snippet}
	{#snippet footer()}
		<Button variant="secondary" size="sm" onclick={() => (confirmOpen = false)}>Cancel</Button>
		<Button
			variant={pendingAction === 'terminate' ? 'danger' : 'primary'}
			size="sm"
			loading={acting}
			onclick={confirm}
		>
			Confirm
		</Button>
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
		margin-bottom: var(--space-5);
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
		flex-wrap: wrap;
		gap: var(--space-3);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.confirm {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}
</style>
