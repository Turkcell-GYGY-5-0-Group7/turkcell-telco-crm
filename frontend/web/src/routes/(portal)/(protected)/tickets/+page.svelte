<script lang="ts">
	// Support view: open a ticket, and find the ones you have opened. ticket-service
	// has no "list my tickets" endpoint (only open / get-by-id / comment), so this
	// page remembers the ids it creates in localStorage (scoped to the caller's
	// customerId, see $lib/tickets/ticket-registry) and offers them back, plus a
	// look-up-by-id box for a ticket opened on another device. Opening requires a
	// linked customer, so an unlinked first-run user gets the onboarding notice.
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { ApiError, api } from '$lib/api/client';
	import { currentCustomerId } from '$lib/auth/session';
	import { listTickets, rememberTicket, type RememberedTicket } from '$lib/tickets/ticket-registry';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import Input from '$lib/ui/Input.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Select from '$lib/ui/Select.svelte';
	import { toasts } from '$lib/ui/toast.svelte';

	const CATEGORIES = [
		{ value: 'TECHNICAL', label: 'Technical' },
		{ value: 'BILLING', label: 'Billing' },
		{ value: 'GENERAL', label: 'General' }
	];
	const PRIORITIES = [
		{ value: 'LOW', label: 'Low' },
		{ value: 'MEDIUM', label: 'Medium' },
		{ value: 'HIGH', label: 'High' }
	];

	let customerId = $state<string | null>(null);
	let remembered = $state<RememberedTicket[]>([]);

	let category = $state('TECHNICAL');
	let priority = $state('MEDIUM');
	let subject = $state('');
	let subjectError = $state('');
	let submitting = $state(false);

	let lookupId = $state('');

	onMount(() => {
		customerId = currentCustomerId();
		if (customerId && typeof localStorage !== 'undefined') {
			remembered = listTickets(localStorage, customerId);
		}
	});

	async function open() {
		subjectError = '';
		if (subject.trim().length < 3) {
			subjectError = 'Please describe the issue in a few words.';
			return;
		}
		submitting = true;
		try {
			const id = await api.openTicket({ category, priority, subject: subject.trim() });
			if (customerId && typeof localStorage !== 'undefined') {
				remembered = rememberTicket(localStorage, customerId, { id, subject: subject.trim() });
			}
			toasts.success('Ticket opened.');
			subject = '';
			await goto(`/tickets/${id}`);
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? (err.serverMessage ?? `Could not open the ticket. (HTTP ${err.status})`)
					: 'Could not open the ticket.'
			);
		} finally {
			submitting = false;
		}
	}

	function lookup() {
		const id = lookupId.trim();
		if (id) void goto(`/tickets/${id}`);
	}
</script>

<section class="page">
	<PageHeader title="Support" subtitle="Open a support ticket and track its progress." />

	{#if customerId === null}
		<NotOnboardedNotice
			message="You need an active account to open a support ticket. Complete onboarding and you can raise and track tickets here."
		/>
	{:else}
		<div class="grid">
			<Card>
				<h2>Open a ticket</h2>
				<div class="form">
					<div class="row">
						<Select
							id="ticket-category"
							label="Category"
							bind:value={category}
							options={CATEGORIES}
						/>
						<Select
							id="ticket-priority"
							label="Priority"
							bind:value={priority}
							options={PRIORITIES}
						/>
					</div>
					<Input
						id="ticket-subject"
						label="Subject"
						bind:value={subject}
						error={subjectError}
						placeholder="Briefly describe the issue"
						required
					/>
					<div class="submit">
						<Button loading={submitting} onclick={open}>Open ticket</Button>
					</div>
				</div>
			</Card>

			<Card>
				<h2>Find a ticket</h2>
				<p class="hint">Have a ticket id? Look it up to see its status and add a comment.</p>
				<div class="lookup">
					<Input id="ticket-lookup" label="Ticket id" bind:value={lookupId} placeholder="UUID" />
					<Button variant="secondary" size="sm" onclick={lookup}>Open</Button>
				</div>
			</Card>
		</div>

		<Card padding="none">
			{#snippet header()}<h2 class="card-head">Your recent tickets</h2>{/snippet}
			{#if remembered.length > 0}
				<ul class="ticket-list">
					{#each remembered as ticket (ticket.id)}
						<li>
							<a href={`/tickets/${ticket.id}`}>
								<span class="subject">{ticket.subject}</span>
								<span class="id mono">{ticket.id.slice(0, 8)}</span>
							</a>
						</li>
					{/each}
				</ul>
			{:else}
				<div class="empty-wrap">
					<EmptyState
						title="No tickets yet"
						message="Tickets you open in this browser are listed here for quick access. They are also always reachable by id above."
					/>
				</div>
			{/if}
		</Card>
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 60rem;
	}

	.grid {
		display: grid;
		grid-template-columns: 1.4fr 1fr;
		gap: var(--space-4);
		align-items: start;
	}

	h2 {
		margin-bottom: var(--space-4);
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.card-head {
		margin: 0;
	}

	.form {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-4);
	}

	.submit {
		display: flex;
		justify-content: flex-end;
	}

	.hint {
		margin-bottom: var(--space-4);
		font-size: var(--text-sm-size);
		color: var(--color-text-muted);
	}

	.lookup {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: end;
		gap: var(--space-3);
	}

	.ticket-list {
		list-style: none;
	}

	.ticket-list a {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-3) var(--space-5);
		border-bottom: 1px solid var(--color-border);
		text-decoration: none;
		color: var(--color-text);
	}

	.ticket-list li:last-child a {
		border-bottom: 0;
	}

	.ticket-list a:hover {
		background: var(--color-surface-alt);
	}

	.subject {
		font-size: var(--text-sm-size);
		font-weight: 600;
	}

	.id,
	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.empty-wrap {
		padding: var(--space-6);
	}

	@media (max-width: 48rem) {
		.grid {
			grid-template-columns: 1fr;
		}
	}
</style>
