<script lang="ts">
	// Account view (16.5.2): the logged-in user's profile plus every subscription
	// with a per-subscription usage/quota gauge, from a SINGLE GET /bff/v1/account
	// call (ApiClient.getAccount). This page is the thin SvelteKit adapter - it
	// holds reactive load state and renders the composed AccountOverview; the usage
	// math lives in the pure, unit-tested $lib/account modules. The read is scoped
	// server-side to the caller (16.5.1); the client sends no id.
	//
	// The (protected) group is ssr=false, so getAccount runs in the browser on
	// mount, carrying the bearer the client attaches automatically. Loading,
	// not-yet-onboarded, error and empty states are handled honestly - a failed load
	// shows a message, never a blank page or a raw throw.
	//
	// Authenticated does NOT imply onboarded: a user with no linked customer record
	// is correctly refused by the BFF's self-scoping guard (403). That is an expected
	// application state, so `loadLinkedResource` separates it from real failures (it
	// also spends one silent renew + retry on it, for the token that predates the
	// customerId claim) and the page invites the user to onboard instead of showing
	// an HTTP error.
	import { onMount } from 'svelte';
	import { ApiError, api, type AccountOverview } from '$lib/api/client';
	import { renewSession } from '$lib/auth/oidc';
	import { loadLinkedResource } from '$lib/onboarding/link-state';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import SubscriptionCard from '$lib/account/SubscriptionCard.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import { customerTone } from '$lib/ui/status';

	let account = $state<AccountOverview | null>(null);
	let loading = $state(true);
	let error = $state('');
	let notOnboarded = $state(false);

	// Up to two initials for the avatar. Purely decorative (the full name is right
	// beside it), so an empty or single-word name simply yields fewer letters.
	const initials = $derived(
		(account?.profile.fullName ?? '')
			.split(/\s+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((part) => part[0]?.toUpperCase() ?? '')
			.join('')
	);

	onMount(() => {
		void load();
	});

	async function load() {
		loading = true;
		error = '';
		notOnboarded = false;
		account = null;
		const result = await loadLinkedResource(() => api.getAccount(), { renewSession });
		if (result.state === 'loaded') {
			account = result.data;
		} else if (result.state === 'unlinked') {
			notOnboarded = true;
		} else {
			error =
				result.error instanceof ApiError
					? `Could not load your account. (HTTP ${result.error.status})`
					: 'Could not load your account.';
		}
		loading = false;
	}
</script>

<section class="page">
	<PageHeader title="Account" subtitle="Your profile, lines, and usage this period." />

	{#if loading}
		<div class="stack" aria-busy="true" aria-label="Loading your account">
			<Card>
				<div class="profile-skeleton">
					<Skeleton variant="circle" width="3rem" height="3rem" />
					<div class="profile-skeleton-text">
						<Skeleton variant="text" width="40%" />
						<Skeleton variant="text" width="60%" />
					</div>
				</div>
			</Card>
			<div class="list">
				{#each [0, 1] as index (index)}
					<Card>
						<div class="stack">
							<Skeleton variant="text" width="50%" />
							<Skeleton variant="block" height="2.5rem" />
							<Skeleton variant="block" height="2.5rem" />
						</div>
					</Card>
				{/each}
			</div>
		</div>
	{:else if error}
		<Alert tone="danger">
			{#snippet children()}
				<p>{error}</p>
			{/snippet}
			{#snippet actions()}
				<Button variant="secondary" size="sm" onclick={() => load()}>Retry</Button>
			{/snippet}
		</Alert>
	{:else if notOnboarded}
		<NotOnboardedNotice
			message="You have not completed onboarding yet, so there is no profile or subscription to show. Your details, lines, and usage will appear here once your subscription is activated."
		/>
	{:else if account}
		<Card>
			<div class="profile">
				<span class="avatar" aria-hidden="true">{initials}</span>
				<div class="identity">
					<span class="name">{account.profile.fullName}</span>
					<span class="customer-id">Customer {account.profile.customerId}</span>
				</div>
				<Badge tone={customerTone(account.profile.status)}>{account.profile.status}</Badge>
			</div>
		</Card>

		<h2>Subscriptions</h2>
		{#if account.subscriptions.length > 0}
			<div class="list">
				{#each account.subscriptions as entry (entry.subscription.subscriptionId)}
					<SubscriptionCard {entry} />
				{/each}
			</div>
		{:else}
			<EmptyState
				title="No subscriptions yet"
				message="Lines you activate will appear here, with their remaining data, minutes, and SMS."
			>
				{#snippet action()}
					<Button href="/onboarding">Start onboarding</Button>
				{/snippet}
			</EmptyState>
		{/if}
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 56rem;
	}

	h2 {
		margin-top: var(--space-2);
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	.stack {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.list {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
		gap: var(--space-4);
	}

	.profile {
		display: flex;
		align-items: center;
		gap: var(--space-4);
	}

	.avatar {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 3rem;
		height: 3rem;
		flex-shrink: 0;
		border-radius: var(--radius-full);
		/* Deliberately the navy ramp, not --color-brand: brand inverts to a light
		   navy in dark mode, and yellow initials on it would fall under AA. */
		background: var(--tk-navy-800);
		color: var(--color-accent);
		font-weight: 700;
		font-size: var(--text-base-size);
		letter-spacing: 0.02em;
	}

	.identity {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}

	.name {
		font-size: var(--text-xl-size);
		line-height: var(--text-xl-lh);
		font-weight: 600;
	}

	.customer-id {
		font-family: var(--font-mono);
		font-size: var(--text-sm-size);
		color: var(--color-text-muted);
		overflow-wrap: anywhere;
	}

	.profile :global(.badge) {
		margin-left: auto;
	}

	.profile-skeleton {
		display: flex;
		align-items: center;
		gap: var(--space-4);
	}

	.profile-skeleton-text {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		flex: 1;
	}
</style>
