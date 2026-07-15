<script lang="ts">
	// Shared root layout for the Telco CRM web app shell (ADR-022): brand, primary
	// navigation, theme switch, and the auth-state affordance. Auth wiring (Keycloak
	// PKCE session, login/logout) lives in $lib/auth/oidc; route guards are in the
	// (protected) group.
	//
	// The header is navy in BOTH themes. It is the brand anchor, and it is what gives
	// the yellow active-underline a surface it actually has contrast against - the
	// same yellow on a light page surface would be unreadable.
	//
	// Below 900px the nav collapses into a drawer. The drawer starts closed, so the
	// server-rendered home route emits nothing for it and hydration has nothing to
	// reconcile.
	import { onMount } from 'svelte';
	import { fade, fly } from 'svelte/transition';
	import { page } from '$app/stores';
	import { authState, initAuth } from '$lib/auth/oidc';
	import { theme } from '$lib/theme/theme.svelte';
	import BrandLogo from '$lib/ui/BrandLogo.svelte';
	import Button from '$lib/ui/Button.svelte';
	import ThemeToggle from '$lib/ui/ThemeToggle.svelte';
	import ToastViewport from '$lib/ui/ToastViewport.svelte';
	import { motionDuration } from '$lib/ui/motion';
	import '$lib/styles/tokens.css';
	import '$lib/styles/base.css';

	let { children } = $props();

	// Register the BFF bearer-token seam and probe any existing Keycloak session;
	// adopt the theme the pre-paint script in app.html already applied to <html>.
	onMount(() => {
		void initAuth();
		theme.init();
	});

	const navItems = [
		{ href: '/', label: 'Home' },
		{ href: '/onboarding', label: 'Onboarding' },
		{ href: '/account', label: 'Account' },
		{ href: '/invoices', label: 'Invoices' }
	];

	const currentPath = $derived($page.url.pathname);

	let menuOpen = $state(false);

	// Home matches only itself; every other item also owns its subpaths. Matching '/'
	// by prefix would mark it current on every page.
	function isCurrent(href: string, path: string): boolean {
		return href === '/' ? path === '/' : path === href || path.startsWith(`${href}/`);
	}

	// A navigation must not leave the drawer covering the page it just opened.
	$effect(() => {
		void currentPath;
		menuOpen = false;
	});

	// While the drawer is open it is the page; the content behind it must not scroll.
	$effect(() => {
		if (typeof document === 'undefined') {
			return;
		}
		document.body.style.overflow = menuOpen ? 'hidden' : '';
		return () => {
			document.body.style.overflow = '';
		};
	});

	function onWindowKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && menuOpen) {
			menuOpen = false;
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<div class="app-shell">
	<header class="app-header">
		<div class="header-inner">
			<a class="brand-link" href="/" aria-label="Telco CRM home">
				<BrandLogo />
			</a>

			<nav class="app-nav" aria-label="Primary">
				{#each navItems as item (item.href)}
					<a href={item.href} aria-current={isCurrent(item.href, currentPath) ? 'page' : undefined}>
						{item.label}
					</a>
				{/each}
			</nav>

			<div class="header-right">
				<ThemeToggle />

				<!-- Auth-state affordance. Reflects the Keycloak session. -->
				<div class="app-auth">
					{#if $authState.status === 'authenticated'}
						<span class="user">{$authState.username ?? 'Signed in'}</span>
						<a class="account-link" href="/login">Account</a>
					{:else}
						<Button href="/login" size="sm">Sign in</Button>
					{/if}
				</div>

				<button
					type="button"
					class="menu-button"
					aria-label={menuOpen ? 'Close menu' : 'Open menu'}
					aria-expanded={menuOpen}
					aria-controls="mobile-nav"
					onclick={() => (menuOpen = !menuOpen)}
				>
					<svg viewBox="0 0 24 24" aria-hidden="true">
						{#if menuOpen}
							<path d="M6 6l12 12M18 6L6 18" />
						{:else}
							<path d="M4 7h16M4 12h16M4 17h16" />
						{/if}
					</svg>
				</button>
			</div>
		</div>
	</header>

	{#if menuOpen}
		<button
			type="button"
			class="scrim"
			aria-label="Close menu"
			tabindex="-1"
			onclick={() => (menuOpen = false)}
			transition:fade={{ duration: motionDuration(150) }}
		></button>

		<nav
			id="mobile-nav"
			class="drawer"
			aria-label="Primary"
			transition:fly={{ x: 280, duration: motionDuration(220) }}
		>
			{#each navItems as item (item.href)}
				<a href={item.href} aria-current={isCurrent(item.href, currentPath) ? 'page' : undefined}>
					{item.label}
				</a>
			{/each}

			<div class="drawer-foot">
				{#if $authState.status === 'authenticated'}
					<span class="user">{$authState.username ?? 'Signed in'}</span>
					<a class="account-link" href="/login">Account</a>
				{:else}
					<Button href="/login" size="sm">Sign in</Button>
				{/if}
			</div>
		</nav>
	{/if}

	<main class="app-main">
		<div class="main-inner">
			{#key currentPath}
				<div in:fade={{ duration: motionDuration(160) }}>
					{@render children()}
				</div>
			{/key}
		</div>
	</main>

	<footer class="app-footer">
		<div class="footer-inner">
			<small>Turkcell Telco CRM - demo platform</small>
		</div>
	</footer>
</div>

<ToastViewport />

<style>
	.app-shell {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
	}

	/* The header keeps its own focus colour: the app-wide navy ring would vanish
	   against a navy bar, so inside here the ring is the yellow. */
	.app-header {
		position: sticky;
		top: 0;
		z-index: var(--z-header);
		background: var(--color-header-bg);
		border-bottom: 1px solid var(--color-header-border);
		--color-focus: var(--color-accent);
	}

	.header-inner,
	.main-inner,
	.footer-inner {
		width: 100%;
		max-width: var(--container-max);
		margin-inline: auto;
		padding-inline: var(--space-6);
	}

	.header-inner {
		display: flex;
		align-items: center;
		gap: var(--space-8);
		height: 4rem;
	}

	.brand-link {
		display: inline-flex;
		text-decoration: none;
		border-radius: var(--radius-md);
	}

	.app-nav {
		display: flex;
		align-items: center;
		gap: var(--space-6);
		height: 100%;
	}

	.app-nav a {
		display: inline-flex;
		align-items: center;
		height: 100%;
		color: var(--color-header-muted);
		font-size: var(--text-sm-size);
		font-weight: 600;
		text-decoration: none;
		border-bottom: 2px solid transparent;
		transition: color var(--duration-fast) var(--ease-out);
	}

	.app-nav a:hover {
		color: var(--color-header-text);
	}

	.app-nav a[aria-current='page'] {
		color: var(--color-header-text);
		border-bottom-color: var(--color-accent);
	}

	.header-right {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-left: auto;
	}

	.app-auth {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.user {
		color: var(--color-header-muted);
		font-size: var(--text-sm-size);
	}

	.account-link {
		color: var(--color-header-text);
		font-size: var(--text-sm-size);
		font-weight: 600;
		text-decoration: none;
		padding: 0.35rem 0.8rem;
		border: 1px solid var(--color-header-border);
		border-radius: var(--radius-md);
		transition: background-color var(--duration-fast) var(--ease-out);
	}

	.account-link:hover {
		background: rgb(255 255 255 / 0.08);
	}

	.menu-button {
		display: none;
		align-items: center;
		justify-content: center;
		width: 2.25rem;
		height: 2.25rem;
		padding: 0;
		border: 1px solid var(--color-header-border);
		border-radius: var(--radius-md);
		background: transparent;
		color: var(--color-header-text);
		cursor: pointer;
	}

	.menu-button svg {
		width: 1.25rem;
		height: 1.25rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
	}

	.scrim {
		position: fixed;
		inset: 0;
		z-index: var(--z-drawer);
		border: 0;
		padding: 0;
		background: rgb(10 15 31 / 0.55);
		cursor: pointer;
	}

	.drawer {
		position: fixed;
		top: 0;
		right: 0;
		bottom: 0;
		z-index: var(--z-drawer);
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		width: min(17.5rem, 85vw);
		padding: var(--space-6) var(--space-5);
		background: var(--color-header-bg);
		border-left: 1px solid var(--color-header-border);
		box-shadow: var(--shadow-lg);
		--color-focus: var(--color-accent);
	}

	.drawer a {
		padding: var(--space-3) var(--space-3);
		border-radius: var(--radius-md);
		color: var(--color-header-muted);
		font-size: var(--text-base-size);
		font-weight: 600;
		text-decoration: none;
	}

	.drawer a[aria-current='page'] {
		color: var(--color-header-text);
		background: rgb(255 255 255 / 0.08);
		box-shadow: inset 3px 0 0 var(--color-accent);
	}

	.drawer-foot {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-top: auto;
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-header-border);
	}

	.app-main {
		flex: 1;
		padding-block: var(--space-8);
	}

	.app-footer {
		padding-block: var(--space-6);
		border-top: 1px solid var(--color-border);
		color: var(--color-text-muted);
	}

	@media (max-width: 56rem) {
		.header-inner,
		.main-inner,
		.footer-inner {
			padding-inline: var(--space-4);
		}

		.app-nav,
		.header-right .app-auth {
			display: none;
		}

		.menu-button {
			display: inline-flex;
		}
	}
</style>
