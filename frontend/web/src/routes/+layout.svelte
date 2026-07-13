<script lang="ts">
	// Shared root layout for the Telco CRM web app shell (ADR-022): brand,
	// primary navigation, and the auth-state affordance. Auth wiring (Keycloak
	// PKCE session, login/logout) is subtask 16.3.1; route guards are 16.3.2.
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { authState, initAuth } from '$lib/auth/oidc';

	let { children } = $props();

	// Register the BFF bearer-token seam and probe any existing Keycloak session.
	onMount(() => {
		void initAuth();
	});

	const navItems = [
		{ href: '/', label: 'Home' },
		{ href: '/onboarding', label: 'Onboarding' },
		{ href: '/account', label: 'Account' },
		{ href: '/invoices', label: 'Invoices' }
	];

	const currentPath = $derived($page.url.pathname);
</script>

<div class="app-shell">
	<header class="app-header">
		<span class="brand">Telco CRM</span>

		<nav class="app-nav" aria-label="Primary">
			{#each navItems as item (item.href)}
				<a href={item.href} aria-current={currentPath === item.href ? 'page' : undefined}>
					{item.label}
				</a>
			{/each}
		</nav>

		<!-- Auth-state affordance (16.3.1). Reflects the Keycloak session. -->
		<div class="app-auth">
			{#if $authState.status === 'authenticated'}
				<span class="user">{$authState.username ?? 'Signed in'}</span>
				<a href="/login" class="login-link">Account</a>
			{:else}
				<a href="/login" class="login-link">Sign in</a>
			{/if}
		</div>
	</header>

	<main class="app-main">
		{@render children()}
	</main>

	<footer class="app-footer">
		<small>Telco CRM Platform</small>
	</footer>
</div>

<style>
	:global(body) {
		margin: 0;
		font-family:
			system-ui,
			-apple-system,
			'Segoe UI',
			Roboto,
			sans-serif;
		color: #1a1a2e;
		background: #f5f6fa;
	}

	.app-shell {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
	}

	.app-header {
		display: flex;
		align-items: center;
		gap: 1.5rem;
		padding: 1rem 1.5rem;
		background: #16213e;
		color: #ffffff;
	}

	.brand {
		font-weight: 600;
		letter-spacing: 0.02em;
	}

	.app-nav {
		display: flex;
		gap: 1rem;
	}

	.app-nav a {
		color: #c7d2fe;
		text-decoration: none;
	}

	.app-nav a[aria-current='page'] {
		color: #ffffff;
		font-weight: 600;
	}

	.app-auth {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.user {
		color: #c7d2fe;
		font-size: 0.9rem;
	}

	.login-link {
		color: #ffffff;
		text-decoration: none;
		border: 1px solid #3f5185;
		padding: 0.35rem 0.75rem;
		border-radius: 0.375rem;
	}

	.app-main {
		flex: 1;
		padding: 2rem 1.5rem;
	}

	.app-footer {
		padding: 1rem 1.5rem;
		color: #6b7280;
	}
</style>
