<script lang="ts">
	// The CRM console's left navigation. Renders the role-filtered nav model
	// (`$lib/crm/nav`), marks the active section, and badges the planned (unwired)
	// sections honestly. A footer carries a link back to the subscriber portal and a
	// link to the gateway's API explorer (Swagger UI). Note: the domain services do
	// not currently expose their OpenAPI (`/v3/api-docs`), so the explorer's per-service
	// definitions do not load - the label reflects that this is the gateway explorer,
	// not a full spec browser. Deep-linked to /swagger-ui/index.html to skip the
	// /swagger-ui.html redirect hop.
	import { resolveBaseUrl } from '$lib/api/client';
	import Badge from '$lib/ui/Badge.svelte';
	import { isCurrentCrm, visibleNavItems } from './nav';

	let {
		roles,
		currentPath
	}: {
		roles: string[];
		currentPath: string;
	} = $props();

	const items = $derived(visibleNavItems(roles));
	const swaggerUrl = $derived(`${resolveBaseUrl().replace(/\/+$/, '')}/swagger-ui/index.html`);
</script>

<nav class="sidebar" aria-label="CRM sections">
	<div class="nav-items">
		{#each items as item (item.href)}
			<a href={item.href} aria-current={isCurrentCrm(item.href, currentPath) ? 'page' : undefined}>
				<span>{item.label}</span>
				{#if !item.wired}
					<Badge tone="info">{#snippet children()}Planned{/snippet}</Badge>
				{/if}
			</a>
		{/each}
	</div>

	<div class="sidebar-foot">
		<a class="foot-link" href="/">Back to portal</a>
		<a class="foot-link" href={swaggerUrl} target="_blank" rel="noreferrer noopener">
			API Explorer (gateway)
		</a>
	</div>
</nav>

<style>
	.sidebar {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
		height: 100%;
		padding: var(--space-5) var(--space-4);
	}

	.nav-items {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.sidebar a {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-3);
		border-radius: var(--radius-md);
		color: var(--color-header-muted);
		font-size: var(--text-sm-size);
		font-weight: 600;
		text-decoration: none;
		transition: background-color var(--duration-fast) var(--ease-out);
	}

	.sidebar a:hover {
		color: var(--color-header-text);
		background: rgb(255 255 255 / 0.06);
	}

	.sidebar a[aria-current='page'] {
		color: var(--color-header-text);
		background: rgb(255 255 255 / 0.1);
		box-shadow: inset 3px 0 0 var(--color-accent);
	}

	.sidebar-foot {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		margin-top: auto;
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-header-border);
	}

	.foot-link {
		font-size: var(--text-xs-size) !important;
		color: var(--color-header-muted);
	}
</style>
