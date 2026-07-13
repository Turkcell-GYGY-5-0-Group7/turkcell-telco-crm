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

	let account = $state<AccountOverview | null>(null);
	let loading = $state(true);
	let error = $state('');
	let notOnboarded = $state(false);

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
	<h1>Account</h1>

	{#if loading}
		<p class="hint">Loading your account...</p>
	{:else if error}
		<div class="notice error" role="alert">
			<p>{error}</p>
			<button type="button" onclick={() => load()}>Retry</button>
		</div>
	{:else if notOnboarded}
		<NotOnboardedNotice
			message="You have not completed onboarding yet, so there is no profile or subscription to show. Your details, lines, and usage will appear here once your subscription is activated."
		/>
	{:else if account}
		<div class="profile">
			<span class="name">{account.profile.fullName}</span>
			<span class="meta">Customer {account.profile.customerId}</span>
			<span class="meta">Status: {account.profile.status}</span>
		</div>

		<h2>Subscriptions</h2>
		{#if account.subscriptions.length > 0}
			<div class="list">
				{#each account.subscriptions as entry (entry.subscription.subscriptionId)}
					<SubscriptionCard {entry} />
				{/each}
			</div>
		{:else}
			<p class="hint">You have no subscriptions yet.</p>
		{/if}
	{/if}
</section>

<style>
	.page {
		max-width: 44rem;
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	h1 {
		margin: 0;
		font-size: 1.5rem;
	}

	h2 {
		margin: 0.5rem 0 0;
		font-size: 1.15rem;
	}

	.profile {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
		background: #ffffff;
		border: 1px solid #e5e7eb;
		border-radius: 0.75rem;
		padding: 1.25rem;
	}

	.profile .name {
		font-weight: 600;
		font-size: 1.15rem;
	}

	.profile .meta {
		color: #6b7280;
		font-size: 0.85rem;
	}

	.list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.notice {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1rem 1.25rem;
		border-radius: 0.5rem;
	}

	.notice.error {
		background: #fef2f2;
		border: 1px solid #fecaca;
	}

	.notice.error p {
		margin: 0;
		color: #b91c1c;
		font-size: 0.9rem;
	}

	.notice button {
		font: inherit;
		font-size: 0.85rem;
		padding: 0.35rem 0.9rem;
		border-radius: 0.375rem;
		border: 1px solid #b91c1c;
		background: #ffffff;
		color: #b91c1c;
		cursor: pointer;
	}
</style>
