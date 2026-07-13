<script lang="ts">
	// Post-login dashboard / public home (16.5.3). This route is `/` and, unlike the
	// (protected) group, is NOT ssr=false - it can be server-rendered. So every
	// browser-only action (the getHome fetch, login()) is kept off the SSR path:
	// the data load runs inside a $effect (which does not execute during SSR) and
	// only once the reactive authState reports 'authenticated'.
	//
	// The page branches honestly on authState:
	//   - unknown       -> the initial session probe (and the SSR render): neutral.
	//   - anonymous      -> a welcome + Sign in prompt; NO getHome call (it would 401).
	//   - authenticated  -> a single api.getHome() -> the dashboard summary, with
	//                       loading / not-yet-onboarded / error / empty states handled.
	// The dashboard is a SUMMARY with links into /account and /invoices; it does not
	// reproduce those full views.
	//
	// An authenticated user who has NOT onboarded has no linked customer record, so
	// the BFF's self-scoping guard correctly rejects GET /bff/v1/home with 403. That
	// is this app's most common first-run state, not a fault: `loadLinkedResource`
	// classifies it (and, once, silently renews the token first, to cover the token
	// minted before identity-service consumed customer.registered.v1) and the page
	// answers with a welcome + onboarding CTA. Genuine failures - a 500, a network
	// error, any non-403 - still surface as the real error.
	import { ApiError, api, type HomeDashboard } from '$lib/api/client';
	import { authState, login, renewSession } from '$lib/auth/oidc';
	import { loadLinkedResource } from '$lib/onboarding/link-state';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import DashboardSummary from '$lib/home/DashboardSummary.svelte';

	let home = $state<HomeDashboard | null>(null);
	let loading = $state(false);
	let error = $state('');
	let notOnboarded = $state(false);
	let signingIn = $state(false);

	// Non-reactive guard so the effect loads once per authenticated transition and
	// resets when the session ends - without re-reading load state reactively.
	let lastStatus: string | null = null;

	// Runs browser-only (SSR skips $effect): react to the session probe and drive
	// the dashboard load off authState, never issuing getHome for an anonymous user.
	$effect(() => {
		const status = $authState.status;
		if (status === lastStatus) {
			return;
		}
		lastStatus = status;
		if (status === 'authenticated') {
			void load();
		} else {
			home = null;
			error = '';
			notOnboarded = false;
			loading = false;
		}
	});

	async function load() {
		loading = true;
		error = '';
		notOnboarded = false;
		home = null;
		// One silent renew + one retry is attempted ONLY on the unlinked (403) path,
		// and only once - if the refreshed token still carries no customerId we fall
		// back to the onboarding CTA rather than looping.
		const result = await loadLinkedResource(() => api.getHome(), { renewSession });
		if (result.state === 'loaded') {
			home = result.data;
		} else if (result.state === 'unlinked') {
			notOnboarded = true;
		} else {
			error =
				result.error instanceof ApiError
					? `Could not load your dashboard. (HTTP ${result.error.status})`
					: 'Could not load your dashboard.';
		}
		loading = false;
	}

	async function signIn() {
		signingIn = true;
		try {
			await login();
		} catch {
			signingIn = false;
		}
	}
</script>

<section class="home">
	<h1>Telco CRM</h1>

	{#if $authState.status === 'authenticated'}
		<p class="lede">Welcome back{$authState.username ? `, ${$authState.username}` : ''}.</p>

		{#if loading}
			<p class="hint">Loading your dashboard...</p>
		{:else if error}
			<div class="notice error" role="alert">
				<p>{error}</p>
				<button type="button" onclick={() => load()}>Retry</button>
			</div>
		{:else if notOnboarded}
			<NotOnboardedNotice
				message="You have not completed onboarding yet, so there is no account to show. Once you have registered, chosen a plan, and your subscription is activated, your dashboard will appear here."
			/>
		{:else if home}
			<DashboardSummary {home} />
		{/if}
	{:else if $authState.status === 'anonymous'}
		<p class="lede">Your account, subscriptions, and invoices in one place.</p>
		<p class="hint">Sign in to see your dashboard.</p>
		<div class="actions">
			<button type="button" onclick={signIn} disabled={signingIn}>Sign in</button>
			<a class="secondary" href="/onboarding">New here? Start onboarding</a>
		</div>
	{:else}
		<p class="hint">Starting up...</p>
	{/if}
</section>

<style>
	.home {
		max-width: 60rem;
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	h1 {
		margin: 0;
		font-size: 1.75rem;
	}

	.lede {
		margin: 0;
		font-size: 1.05rem;
		color: #374151;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.actions {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	.actions button {
		font: inherit;
		padding: 0.5rem 1.1rem;
		border: 1px solid #16213e;
		border-radius: 0.375rem;
		background: #16213e;
		color: #ffffff;
		cursor: pointer;
	}

	.actions button:disabled {
		opacity: 0.6;
		cursor: default;
	}

	.secondary {
		color: #16213e;
		font-size: 0.9rem;
		text-decoration: none;
		font-weight: 600;
	}

	.secondary:hover {
		text-decoration: underline;
	}

	.notice {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1rem 1.25rem;
		border-radius: 0.5rem;
		background: #fef2f2;
		border: 1px solid #fecaca;
	}

	.notice p {
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
