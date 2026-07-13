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
	<h1>Sign in</h1>

	{#if $authState.status === 'authenticated'}
		<p>Signed in{$authState.username ? ` as ${$authState.username}` : ''}.</p>
		<button type="button" onclick={onSignOut} disabled={busy}>Sign out</button>
	{:else}
		<p>Authenticate with Keycloak (Authorization Code + PKCE) to continue.</p>
		<button type="button" onclick={onSignIn} disabled={busy || $authState.status === 'unknown'}>
			Sign in with Keycloak
		</button>
	{/if}
</section>

<style>
	.page {
		max-width: 40rem;
	}

	h1 {
		margin: 0 0 0.5rem;
		font-size: 1.5rem;
	}

	p {
		margin: 0 0 1rem;
		color: #4b5563;
	}

	button {
		font: inherit;
		padding: 0.5rem 1rem;
		border: 1px solid #16213e;
		border-radius: 0.375rem;
		background: #16213e;
		color: #ffffff;
		cursor: pointer;
	}

	button:disabled {
		opacity: 0.6;
		cursor: default;
	}
</style>
