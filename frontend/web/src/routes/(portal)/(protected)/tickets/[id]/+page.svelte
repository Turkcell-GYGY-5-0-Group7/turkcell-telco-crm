<script lang="ts">
	// Ticket detail: status, priority, SLA, and the comment thread, with a box to add
	// a comment. Owner-or-admin only (ticket-service checks ownership against the
	// caller's customerId); a 403/404 here reads as "not found or not yours".
	import { onMount } from 'svelte';
	import { page as pageStore } from '$app/stores';
	import { ApiError, api, type Ticket } from '$lib/api/client';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import { ticketTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const ticketId = $derived($pageStore.params.id ?? '');

	let ticket = $state<Ticket | null>(null);
	let loading = $state(true);
	let error = $state('');
	let notFound = $state(false);

	let comment = $state('');
	let posting = $state(false);

	onMount(() => {
		void load();
	});

	async function load() {
		loading = true;
		error = '';
		notFound = false;
		try {
			ticket = await api.getTicket(ticketId);
		} catch (err) {
			if (err instanceof ApiError && (err.status === 403 || err.status === 404)) {
				notFound = true;
			} else {
				error =
					err instanceof ApiError
						? `Could not load the ticket. (HTTP ${err.status})`
						: 'Could not load the ticket.';
			}
		} finally {
			loading = false;
		}
	}

	async function addComment() {
		if (comment.trim().length === 0) return;
		posting = true;
		try {
			await api.addTicketComment(ticketId, comment.trim());
			comment = '';
			await load();
			toasts.success('Comment added.');
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? `Could not add the comment. (HTTP ${err.status})`
					: 'Could not add the comment.'
			);
		} finally {
			posting = false;
		}
	}

	function fmtDateTime(iso: string): string {
		return new Date(iso).toLocaleString();
	}
</script>

<section class="page">
	<PageHeader title="Ticket" subtitle="Status and conversation.">
		{#snippet actions()}
			<Button variant="secondary" size="sm" href="/tickets">Back to support</Button>
		{/snippet}
	</PageHeader>

	{#if loading}
		<Card><Skeleton variant="text" lines={4} /></Card>
	{:else if notFound}
		<Alert tone="warning">
			{#snippet children()}
				<p>We could not find that ticket, or it does not belong to your account.</p>
			{/snippet}
			{#snippet actions()}<Button variant="secondary" size="sm" href="/tickets">Back</Button
				>{/snippet}
		</Alert>
	{:else if error}
		<Alert tone="danger">
			{#snippet children()}<p>{error}</p>{/snippet}
			{#snippet actions()}<Button variant="secondary" size="sm" onclick={load}>Retry</Button
				>{/snippet}
		</Alert>
	{:else if ticket}
		{@const t = ticket}
		<Card>
			<div class="head">
				<h2>{t.subject}</h2>
				<Badge tone={ticketTone(t.status)}>{#snippet children()}{t.status}{/snippet}</Badge>
			</div>
			<div class="meta">
				<div class="field"><span class="k">Category</span><span class="v">{t.category}</span></div>
				<div class="field"><span class="k">Priority</span><span class="v">{t.priority}</span></div>
				<div class="field">
					<span class="k">Assigned team</span>
					<span class="v">{t.assignedTeam ?? 'Unassigned'}</span>
				</div>
				<div class="field">
					<span class="k">Opened</span>
					<span class="v">{fmtDateTime(t.createdAt)}</span>
				</div>
				{#if t.slaDueAt}
					<div class="field">
						<span class="k">SLA due</span>
						<span class="v">
							{fmtDateTime(t.slaDueAt)}
							{#if t.slaBreached}
								<Badge tone="danger">{#snippet children()}Breached{/snippet}</Badge>
							{/if}
						</span>
					</div>
				{/if}
			</div>
		</Card>

		<Card>
			<h2>Conversation</h2>
			{#if t.comments.length > 0}
				<ul class="thread">
					{#each t.comments as c (c.id)}
						<li>
							<div class="c-head">
								<span class="c-author mono">{c.authorId.slice(0, 8)}</span>
								<span class="c-time">{fmtDateTime(c.createdAt)}</span>
							</div>
							<p class="c-body">{c.body}</p>
						</li>
					{/each}
				</ul>
			{:else}
				<p class="muted">No comments yet. Add the first one below.</p>
			{/if}

			{#if t.status !== 'RESOLVED' && t.status !== 'CLOSED'}
				<div class="add">
					<Textarea
						id="new-comment"
						label="Add a comment"
						bind:value={comment}
						rows={3}
						placeholder="Share more detail or ask a question"
					/>
					<div class="add-actions">
						<Button size="sm" loading={posting} onclick={addComment}>Post comment</Button>
					</div>
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
		max-width: 52rem;
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		margin-bottom: var(--space-4);
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
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--text-sm-size);
	}

	h2 {
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.thread {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		margin: var(--space-4) 0;
	}

	.thread li {
		padding: var(--space-3) var(--space-4);
		background: var(--color-surface-alt);
		border-radius: var(--radius-md);
	}

	.c-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		margin-bottom: var(--space-2);
	}

	.c-author,
	.mono {
		font-family: var(--font-mono);
		font-size: var(--text-xs-size);
		color: var(--color-text-secondary);
	}

	.c-time {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.c-body {
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
		white-space: pre-wrap;
	}

	.add {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		margin-top: var(--space-4);
	}

	.add-actions {
		display: flex;
		justify-content: flex-end;
	}

	.muted {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}
</style>
