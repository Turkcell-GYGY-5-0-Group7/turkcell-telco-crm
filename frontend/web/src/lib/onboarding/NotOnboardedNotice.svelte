<script lang="ts">
	// The friendly face of the "authenticated but not yet linked to a customer"
	// state (see $lib/onboarding/link-state.ts). Rendered by the dashboard,
	// /account and /invoices in place of the account data a first-run user does
	// not have yet - and in place of the red HTTP-403 error they used to see.
	//
	// It is an INVITATION, not a failure: hence a surface card with a warm accent
	// bar rather than an Alert's danger tone. A welcome line, a page-specific
	// explanation of what will appear here once onboarding is done, and a single
	// clear call to action into /onboarding. Presentational only (no fetching, no
	// auth), so it is safe on the server-rendered home route as well as inside the
	// ssr=false (protected) group.
	import Button from '$lib/ui/Button.svelte';

	let {
		title = 'Welcome to Telco CRM',
		message,
		cta = 'Start onboarding'
	}: {
		/** Heading of the notice. */
		title?: string;
		/** What the user will see here once they have onboarded. */
		message: string;
		/** Call-to-action label on the /onboarding link. */
		cta?: string;
	} = $props();
</script>

<div class="notice" role="status">
	<span class="accent" aria-hidden="true"></span>
	<span class="icon" aria-hidden="true">
		<svg viewBox="0 0 24 24">
			<path d="M12 4l1.9 4.1 4.6.6-3.4 3.1.9 4.5-4-2.3-4 2.3.9-4.5-3.4-3.1 4.6-.6z" />
		</svg>
	</span>

	<div class="body">
		<h2>{title}</h2>
		<p class="message">{message}</p>
		<p class="hint">
			Onboarding takes a few minutes: your details, an identity document, and the plan you want.
		</p>
		<div class="cta">
			<Button href="/onboarding">{cta}</Button>
		</div>
	</div>
</div>

<style>
	.notice {
		position: relative;
		display: flex;
		align-items: flex-start;
		gap: var(--space-4);
		padding: var(--space-6);
		padding-left: var(--space-6);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		overflow: hidden;
	}

	.accent {
		position: absolute;
		inset: 0 auto 0 0;
		width: 4px;
		background: var(--color-accent);
	}

	.icon {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2.5rem;
		height: 2.5rem;
		flex-shrink: 0;
		border-radius: var(--radius-full);
		background: var(--color-accent-soft);
		color: var(--color-on-accent-soft);
	}

	.icon svg {
		width: 1.25rem;
		height: 1.25rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 1.6;
		stroke-linejoin: round;
	}

	.body {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	h2 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	.message {
		color: var(--color-text-secondary);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
	}

	.cta {
		margin-top: var(--space-3);
	}
</style>
