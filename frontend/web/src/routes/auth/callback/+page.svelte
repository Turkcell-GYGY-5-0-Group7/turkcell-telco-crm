<script lang="ts">
	// OIDC redirect callback (subtask 16.3.1). Keycloak redirects here with the
	// authorization code; oidc-client-ts completes the PKCE exchange, stores the
	// token set, then we route back into the app. The redirect URI
	// `http://localhost:3000/auth/callback` matches the `telco-web` client's
	// registered `http://localhost:3000/*`.
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { completeLogin, returnPathFromUser } from '$lib/auth/oidc';
	import Alert from '$lib/ui/Alert.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';

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
		<Alert tone="danger">
			{#snippet children()}
				<p><strong>Sign-in failed.</strong></p>
				<p>{error}</p>
			{/snippet}
			{#snippet actions()}
				<Button variant="secondary" size="sm" href="/login">Back to sign in</Button>
			{/snippet}
		</Alert>
	{:else}
		<div class="pending">
			<Spinner size="md" />
			<p class="title">Signing in...</p>
			<p class="detail">Completing authentication with Keycloak.</p>
		</div>
	{/if}
</section>

<style>
	.page {
		max-width: 36rem;
		margin-inline: auto;
		padding-block: var(--space-12);
	}

	.pending {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: var(--space-3);
	}

	.title {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 600;
	}

	.detail {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}
</style>
