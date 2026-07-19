<script lang="ts">
	// CRM console shell: a fixed navy sidebar plus a slim topbar, distinct from the
	// subscriber portal's centred topbar shell so the two personas feel different.
	// The sidebar is role-filtered from the layout data; on narrow viewports it
	// collapses into the same drawer pattern the portal uses.
	import { fade, fly } from 'svelte/transition';
	import { page } from '$app/stores';
	import { authState } from '$lib/auth/oidc';
	import BrandLogo from '$lib/ui/BrandLogo.svelte';
	import ThemeToggle from '$lib/ui/ThemeToggle.svelte';
	import { motionDuration } from '$lib/ui/motion';
	import Sidebar from '$lib/crm/Sidebar.svelte';

	let { data, children } = $props();

	const currentPath = $derived($page.url.pathname);
	let menuOpen = $state(false);

	$effect(() => {
		void currentPath;
		menuOpen = false;
	});

	$effect(() => {
		if (typeof document === 'undefined') return;
		document.body.style.overflow = menuOpen ? 'hidden' : '';
		return () => {
			document.body.style.overflow = '';
		};
	});

	function onWindowKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape' && menuOpen) menuOpen = false;
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<div class="crm-shell">
	<aside class="crm-aside">
		<a class="brand-link" href="/crm" aria-label="CRM console home">
			<BrandLogo />
			<span class="console-tag">Console</span>
		</a>
		<Sidebar roles={data.roles} {currentPath} />
	</aside>

	<div class="crm-body">
		<header class="crm-topbar">
			<button
				type="button"
				class="menu-button"
				aria-label={menuOpen ? 'Close menu' : 'Open menu'}
				aria-expanded={menuOpen}
				onclick={() => (menuOpen = !menuOpen)}
			>
				<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
			</button>

			<span class="topbar-title">CRM Console</span>

			<div class="topbar-right">
				<ThemeToggle />
				{#if $authState.status === 'authenticated'}
					<span class="user">{$authState.username ?? 'Signed in'}</span>
				{/if}
				<a class="signout" href="/login">Account</a>
			</div>
		</header>

		<main class="crm-main">
			{#key currentPath}
				<div class="crm-main-inner" in:fade={{ duration: motionDuration(160) }}>
					{@render children()}
				</div>
			{/key}
		</main>
	</div>

	{#if menuOpen}
		<button
			type="button"
			class="scrim"
			aria-label="Close menu"
			tabindex="-1"
			onclick={() => (menuOpen = false)}
			transition:fade={{ duration: motionDuration(150) }}
		></button>

		<aside class="drawer" transition:fly={{ x: -280, duration: motionDuration(220) }}>
			<a class="brand-link" href="/crm" aria-label="CRM console home">
				<BrandLogo />
				<span class="console-tag">Console</span>
			</a>
			<Sidebar roles={data.roles} {currentPath} />
		</aside>
	{/if}
</div>

<style>
	.crm-shell {
		display: flex;
		min-height: 100vh;
	}

	.crm-aside {
		display: flex;
		flex-direction: column;
		width: 15rem;
		flex-shrink: 0;
		background: var(--color-header-bg);
		border-right: 1px solid var(--color-header-border);
		--color-focus: var(--color-accent);
	}

	.brand-link {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-5) var(--space-4);
		text-decoration: none;
		border-bottom: 1px solid var(--color-header-border);
	}

	.console-tag {
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--color-accent);
	}

	.crm-body {
		display: flex;
		flex-direction: column;
		flex: 1;
		min-width: 0;
	}

	.crm-topbar {
		position: sticky;
		top: 0;
		z-index: var(--z-header);
		display: flex;
		align-items: center;
		gap: var(--space-4);
		height: 3.5rem;
		padding-inline: var(--space-6);
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
	}

	.topbar-title {
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.topbar-right {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin-left: auto;
	}

	.user {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}

	.signout {
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text);
		text-decoration: none;
		padding: 0.35rem 0.8rem;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
	}

	.signout:hover {
		background: var(--color-surface-alt);
	}

	.crm-main {
		flex: 1;
		padding: var(--space-8) var(--space-6);
		background: var(--color-bg);
	}

	.crm-main-inner {
		width: 100%;
		max-width: 72rem;
		margin-inline: auto;
	}

	.menu-button {
		display: none;
		align-items: center;
		justify-content: center;
		width: 2.25rem;
		height: 2.25rem;
		padding: 0;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		background: transparent;
		color: var(--color-text);
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
		left: 0;
		bottom: 0;
		z-index: var(--z-drawer);
		display: flex;
		flex-direction: column;
		width: min(15rem, 85vw);
		background: var(--color-header-bg);
		border-right: 1px solid var(--color-header-border);
		box-shadow: var(--shadow-lg);
		--color-focus: var(--color-accent);
		overflow-y: auto;
	}

	@media (max-width: 56rem) {
		.crm-aside {
			display: none;
		}

		.menu-button {
			display: inline-flex;
		}

		.crm-topbar,
		.crm-main {
			padding-inline: var(--space-4);
		}
	}
</style>
