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
	import Alert from '$lib/ui/Alert.svelte';
	import BrandLogo from '$lib/ui/BrandLogo.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';

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

{#if $authState.status === 'authenticated'}
	<section class="page">
		<PageHeader
			title="Dashboard"
			subtitle={$authState.username
				? `Welcome back, ${$authState.username}.`
				: 'Your account at a glance.'}
		/>

		{#if loading}
			<!-- Skeletons mirror the real three-card grid, so the layout does not jump
			     when the composed payload lands. -->
			<div class="skeleton-grid" aria-busy="true" aria-label="Loading your dashboard">
				{#each [0, 1, 2] as index (index)}
					<Card>
						<div class="skeleton-card">
							<Skeleton variant="circle" width="2.5rem" height="2.5rem" />
							<Skeleton variant="text" width="45%" />
							<Skeleton variant="text" lines={3} />
						</div>
					</Card>
				{/each}
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
				message="You have not completed onboarding yet, so there is no account to show. Once you have registered, chosen a plan, and your subscription is activated, your dashboard will appear here."
			/>
		{:else if home}
			<DashboardSummary {home} />
		{/if}
	</section>
{:else if $authState.status === 'anonymous'}
	<section class="hero">
		<BrandLogo variant="hero" />
		<h1>Your telecom, in one place</h1>
		<p class="lede">
			Register a line, follow your usage, and settle your invoices - one account, one view.
		</p>
		<div class="actions">
			<Button onclick={signIn} loading={signingIn}>Sign in</Button>
			<Button variant="ghost" href="/onboarding">New here? Start onboarding</Button>
		</div>
	</section>
{:else}
	<div class="booting">
		<Spinner size="md" label="Starting up" />
	</div>
{/if}

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}

	.skeleton-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
		gap: var(--space-4);
	}

	.skeleton-card {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.hero {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		text-align: center;
		gap: var(--space-5);
		max-width: 42rem;
		min-height: min(66vh, 38rem);
		margin-inline: auto;
		padding-block: var(--space-12);
	}

	h1 {
		font-size: var(--text-4xl-size);
		line-height: var(--text-4xl-lh);
		font-weight: 700;
	}

	.lede {
		color: var(--color-text-secondary);
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
	}

	.actions {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		justify-content: center;
		gap: var(--space-3);
		margin-top: var(--space-2);
	}

	.booting {
		display: flex;
		justify-content: center;
		padding-block: var(--space-16);
	}

	@media (max-width: 40rem) {
		h1 {
			font-size: var(--text-3xl-size);
			line-height: var(--text-3xl-lh);
		}
	}
</style>
