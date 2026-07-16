<script lang="ts">
	// Guards a CRM page's content behind a role the current staff user may not hold.
	// When they lack it, we render an honest notice instead of firing a request that
	// the service would reject anyway (avoiding a wall of 403s). This is a UI courtesy
	// only; the server remains the authority. When they hold a required role, the
	// page's real content (the `children` snippet) renders.
	import type { Snippet } from 'svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';

	let {
		roles,
		allow,
		children
	}: {
		/** The current user's realm roles (from the CRM layout data). */
		roles: string[];
		/** Roles that may see the content; any match grants access. */
		allow: string[];
		children: Snippet;
	} = $props();

	const permitted = $derived(allow.some((role) => roles.includes(role)));
	const allowLabel = $derived(allow.join(' or '));
</script>

{#if permitted}
	{@render children()}
{:else}
	<EmptyState
		title="Restricted section"
		message={`This section is available to ${allowLabel}. Your account does not currently hold that role.`}
	/>
{/if}
