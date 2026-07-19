<script lang="ts">
	// Slim application root. It owns only the concerns that are the same for EVERY
	// area of the app: loading the design tokens, initialising auth (registering the
	// BFF bearer-token seam and probing any existing Keycloak session), adopting the
	// pre-painted theme, and mounting the toast viewport. The visual shell differs by
	// area, so it lives one level down: the subscriber portal renders a topbar shell
	// in `(portal)/+layout.svelte`, and the staff CRM console renders a sidebar shell
	// in `crm/+layout.svelte`. Both nest inside this root.
	import { onMount } from 'svelte';
	import { initAuth } from '$lib/auth/oidc';
	import { theme } from '$lib/theme/theme.svelte';
	import ToastViewport from '$lib/ui/ToastViewport.svelte';
	// Self-hosted Keycloak typeface (Red Hat), bundled by Vite - no external CDN.
	import '@fontsource-variable/red-hat-text';
	import '@fontsource-variable/red-hat-display';
	import '$lib/styles/tokens.css';
	import '$lib/styles/base.css';

	let { children } = $props();

	onMount(() => {
		void initAuth();
		theme.init();
	});
</script>

{@render children()}

<ToastViewport />
