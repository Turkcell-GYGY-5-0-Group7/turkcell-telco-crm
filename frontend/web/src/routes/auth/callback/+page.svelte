<script lang="ts">
	// OIDC redirect callback (subtask 16.3.1). Keycloak redirects here with the
	// authorization code; oidc-client-ts completes the PKCE exchange, stores the
	// token set, then we route back into the app. The redirect URI
	// `http://localhost:3000/auth/callback` matches the `telco-web` client's
	// registered `http://localhost:3000/*`.
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { completeLogin, returnPathFromUser } from '$lib/auth/oidc';

	let error = $state<string | null>(null);

	onMount(async () => {
		try {
			const user = await completeLogin();
			// Return the user to the originally requested route (16.3.2), carried
			// through Keycloak in the OIDC state; defaults to home when absent.
			await goto(returnPathFromUser(user), { replaceState: true });
		} catch (err) {
			error = err instanceof Error ? err.message : 'Sign-in could not be completed.';
		}
	});
</script>

<section class="page">
	{#if error}
		<h1>Sign-in failed</h1>
		<p class="error">{error}</p>
		<a href="/login">Back to sign in</a>
	{:else}
		<h1>Signing in...</h1>
		<p>Completing authentication with Keycloak.</p>
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
		margin: 0 0 0.75rem;
		color: #4b5563;
	}

	.error {
		color: #b91c1c;
	}
</style>
