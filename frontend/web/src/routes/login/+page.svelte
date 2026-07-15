<script lang="ts">
	// Login route (subtask 16.3.1). Exercises the Keycloak Authorization Code +
	// PKCE flow against the `telco-web` public client: "Sign in" redirects to
	// Keycloak; "Sign out" runs RP-initiated logout (ends the SSO session). Route
	// 401-driven redirects are out of scope here (16.3.3). The route guard (16.3.2)
	// sends unauthenticated users here with a `?returnTo=` intended path, which we
	// forward into the login so the callback returns them to where they started.
	import { page } from '$app/stores';
	import { authState, login, logout } from '$lib/auth/oidc';
	import { RETURN_TO_PARAM } from '$lib/auth/route-guard';
	import BrandLogo from '$lib/ui/BrandLogo.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';

	let busy = $state(false);

	const returnTo = $derived($page.url.searchParams.get(RETURN_TO_PARAM) ?? undefined);

	async function onSignIn() {
		busy = true;
		try {
			await login(returnTo);
		} catch {
			busy = false;
		}
	}

	async function onSignOut() {
		busy = true;
		try {
			await logout();
		} catch {
			busy = false;
		}
	}
</script>

<section class="page">
	<Card padding="lg">
		<div class="body">
			<BrandLogo variant="hero" />

			{#if $authState.status === 'authenticated'}
				<h1>Your session</h1>
				<p class="lede">
					Signed in{$authState.username ? ` as ${$authState.username}` : ''}.
				</p>
				<Button variant="secondary" onclick={onSignOut} loading={busy}>Sign out</Button>
			{:else}
				<h1>Sign in</h1>
				<p class="lede">
					Authenticate with Keycloak (Authorization Code + PKCE) to reach your account.
				</p>
				<Button onclick={onSignIn} loading={busy} disabled={$authState.status === 'unknown'}>
					Sign in with Keycloak
				</Button>
			{/if}
		</div>
	</Card>
</section>

<style>
	.page {
		max-width: 26rem;
		margin-inline: auto;
		padding-block: var(--space-12);
	}

	.body {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: var(--space-4);
	}

	h1 {
		font-size: var(--text-2xl-size);
		line-height: var(--text-2xl-lh);
		font-weight: 700;
	}

	.lede {
		color: var(--color-text-secondary);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}

	.body :global(.btn) {
		width: 100%;
		margin-top: var(--space-2);
	}
</style>
