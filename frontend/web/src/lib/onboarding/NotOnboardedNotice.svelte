<script lang="ts">
	// The friendly face of the "authenticated but not yet linked to a customer"
	// state (see $lib/onboarding/link-state.ts). Rendered by the dashboard,
	// /account and /invoices in place of the account data a first-run user does
	// not have yet - and in place of the red HTTP-403 error they used to see.
	//
	// It is an INVITATION, not a failure: a welcome line, a page-specific
	// explanation of what will appear here once onboarding is done, and a single
	// clear call to action into /onboarding. Presentational only (no fetching, no
	// auth), so it is safe on the server-rendered home route as well as inside the
	// ssr=false (protected) group.
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

<div class="onboarding-notice" role="status">
	<h2>{title}</h2>
	<p class="message">{message}</p>
	<p class="hint">
		Onboarding takes a few minutes: your details, an identity document, and the plan you want.
	</p>
	<a class="cta" href="/onboarding">{cta}</a>
</div>

<style>
	.onboarding-notice {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 0.6rem;
		padding: 1.5rem;
		border-radius: 0.75rem;
		background: #ffffff;
		border: 1px solid #e5e7eb;
	}

	h2 {
		margin: 0;
		font-size: 1.15rem;
	}

	.message {
		margin: 0;
		color: #374151;
		font-size: 0.95rem;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.cta {
		margin-top: 0.4rem;
		padding: 0.5rem 1.1rem;
		border: 1px solid #16213e;
		border-radius: 0.375rem;
		background: #16213e;
		color: #ffffff;
		font-size: 0.95rem;
		font-weight: 600;
		text-decoration: none;
	}

	.cta:hover {
		background: #1f2f57;
	}
</style>
