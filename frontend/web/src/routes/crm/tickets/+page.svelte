<script lang="ts">
	// CRM tickets: look up a ticket by id, then assign it to a team or resolve it.
	// ADMIN-only in this console (ticket-service gates assign/resolve to ADMIN/SUPPORT,
	// and SUPPORT is not a realm role here). There is no "list all tickets" endpoint,
	// so a queue view is an honest coming-soon; agents work from a ticket id today.
	import { page } from '$app/stores';
	import { ApiError, api, type Ticket } from '$lib/api/client';
	import RoleGate from '$lib/crm/RoleGate.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import ComingSoon from '$lib/ui/ComingSoon.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Select from '$lib/ui/Select.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import { ticketTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const ALLOW = ['ADMIN'];
	const TEAMS = [
		{ value: 'tech-support', label: 'Technical support' },
		{ value: 'billing-support', label: 'Billing support' },
		{ value: 'customer-care', label: 'Customer care' }
	];
	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);

	let lookupId = $state('');
	let ticket = $state<Ticket | null>(null);
	let loading = $state(false);
	let error = $state('');
	let notFound = $state(false);

	let team = $state('tech-support');
	let assigning = $state(false);
	let resolving = $state(false);

	async function lookup() {
		const id = lookupId.trim();
		if (!id) return;
		loading = true;
		error = '';
		notFound = false;
		ticket = null;
		try {
			ticket = await api.getTicket(id);
		} catch (err) {
			if (err instanceof ApiError && (err.status === 404 || err.status === 403)) notFound = true;
			else
				error = err instanceof ApiError ? `Lookup failed. (HTTP ${err.status})` : 'Lookup failed.';
		} finally {
			loading = false;
		}
	}

	async function assign() {
		if (!ticket) return;
		assigning = true;
		try {
			await api.assignTicket(ticket.id, team);
			toasts.success('Ticket assigned.');
			await lookup();
		} catch (err) {
			toasts.error(
				err instanceof ApiError ? `Assign failed. (HTTP ${err.status})` : 'Assign failed.'
			);
		} finally {
			assigning = false;
		}
	}

	async function resolve() {
		if (!ticket) return;
		resolving = true;
		try {
			await api.resolveTicket(ticket.id);
			toasts.success('Ticket resolved.');
			await lookup();
		} catch (err) {
			toasts.error(
				err instanceof ApiError ? `Resolve failed. (HTTP ${err.status})` : 'Resolve failed.'
			);
		} finally {
			resolving = false;
		}
	}

	function fmtDateTime(iso: string | null): string {
		return iso ? new Date(iso).toLocaleString() : '-';
	}
</script>

<section class="page">
	<PageHeader title="Tickets" subtitle="Assign and resolve support tickets." />

	<RoleGate {roles} allow={ALLOW}>
		{#snippet children()}
			<Card>
				<div class="lookup">
					<Input
						id="crm-ticket-lookup"
						label="Ticket id"
						bind:value={lookupId}
						placeholder="UUID"
						hint="The ticket id returned when a subscriber opens a ticket on the Support page."
					/>
					<Button variant="secondary" size="sm" {loading} onclick={lookup}>Look up</Button>
				</div>
			</Card>

			{#if error}
				<Alert tone="danger">{#snippet children()}<p>{error}</p>{/snippet}</Alert>
			{:else if notFound}
				<Alert tone="warning"
					>{#snippet children()}<p>No ticket was found for that id.</p>{/snippet}</Alert
				>
			{:else if ticket}
				{@const t = ticket}
				<Card>
					<div class="head">
						<h2>{t.subject}</h2>
						<Badge tone={ticketTone(t.status)}>{#snippet children()}{t.status}{/snippet}</Badge>
					</div>
					<div class="meta">
						<div class="field">
							<span class="k">Category</span><span class="v">{t.category}</span>
						</div>
						<div class="field">
							<span class="k">Priority</span><span class="v">{t.priority}</span>
						</div>
						<div class="field">
							<span class="k">Team</span><span class="v">{t.assignedTeam ?? 'Unassigned'}</span>
						</div>
						<div class="field">
							<span class="k">Opened</span><span class="v">{fmtDateTime(t.createdAt)}</span>
						</div>
						{#if t.slaDueAt}
							<div class="field">
								<span class="k">SLA due</span>
								<span class="v">
									{fmtDateTime(t.slaDueAt)}
									{#if t.slaBreached}<Badge tone="danger"
											>{#snippet children()}Breached{/snippet}</Badge
										>{/if}
								</span>
							</div>
						{/if}
					</div>

					{#if t.status !== 'RESOLVED' && t.status !== 'CLOSED'}
						<div class="ops">
							<div class="assign">
								<Select id="assign-team" label="Assign to team" bind:value={team} options={TEAMS} />
								<Button variant="secondary" size="sm" loading={assigning} onclick={assign}
									>Assign</Button
								>
							</div>
							<Button size="sm" loading={resolving} onclick={resolve}>Mark resolved</Button>
						</div>
					{/if}
				</Card>
			{/if}

			<ComingSoon
				title="Ticket queue"
				message="A live queue of all open tickets with filters and SLA sorting is planned. It needs a list endpoint the ticket service does not expose yet; today, work a ticket by its id above."
				cta="Request the queue view"
			/>
		{/snippet}
	</RoleGate>
</section>

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
		align-items: center;
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
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--text-sm-size);
	}

	.ops {
		display: flex;
		align-items: end;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: var(--space-4);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.assign {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: end;
		gap: var(--space-3);
		min-width: 18rem;
	}
</style>
