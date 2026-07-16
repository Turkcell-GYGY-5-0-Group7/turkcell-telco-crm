<script lang="ts">
	// Step 5: activation result + failure/compensation recovery (16.4.2 / 16.4.3).
	// This step shows the TRUE saga outcome sourced from polling
	// GET /api/v1/orders/{id} until the order reaches FULFILLED (activated) or
	// CANCELLED/FAILED (compensated) - never an optimistic assumption.
	//
	// On failure it does not dead-end, but it also does not offer anything the
	// backend cannot honour: the browser has no payment call to retry (charging is
	// event-driven; POST /api/v1/payments is ADMIN-only), and a CANCELLED/FAILED
	// order is terminal. The single honest recovery is to place a NEW order for the
	// same, already-registered customer (BFF `customerId` reuse path, fresh
	// Idempotency-Key), which the page performs.
	import type { PollResult } from '$lib/onboarding/order-status';
	import type { RecoveryAction } from '$lib/onboarding/recovery';
	import Alert from '$lib/ui/Alert.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';

	let {
		polling,
		liveStatus,
		attempts,
		result,
		error,
		recovery,
		onRetryOrder,
		onStartOver,
		onGoToDashboard
	}: {
		polling: boolean;
		liveStatus: string;
		attempts: number;
		result: PollResult | null;
		error: string;
		recovery: RecoveryAction;
		onRetryOrder: () => void;
		onStartOver: () => void;
		/** Continue to the dashboard via a full re-login, so the fresh token carries
		 * the newly-minted customerId claim (see the onboarding page). */
		onGoToDashboard: () => void;
	} = $props();
</script>

<div class="step-body">
	{#if error}
		<Alert tone="danger">
			{#snippet children()}
				<p><strong>We could not confirm your activation.</strong></p>
				<p>{error}</p>
				<p>Your order may still be processing - check your account before ordering again.</p>
			{/snippet}
			{#snippet actions()}
				<Button variant="secondary" size="sm" onclick={onStartOver}>Start over</Button>
			{/snippet}
		</Alert>
	{:else if polling}
		<div class="pending">
			<Spinner size="md" />
			<p class="pending-title">Confirming activation...</p>
			<p class="pending-detail" role="status">
				Status {liveStatus || 'pending'}, attempt {attempts}
			</p>
		</div>
	{:else if result?.outcome === 'activated'}
		<div class="done" role="status">
			<span class="check" aria-hidden="true">
				<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
			</span>
			<h2>Your subscription is active</h2>
			<p class="detail mono">Order {result.orderId} - status {result.status}</p>
			<!--
				Continue via a full re-login (onGoToDashboard), not a plain link: it
				guarantees a fresh token carrying the newly-linked `customerId` claim, so the
				dashboard loads the real account instead of the BFF's unlinked 403. The
				Keycloak SSO session is live, so there is no password prompt.
			-->
			<Button onclick={onGoToDashboard}>Go to my dashboard</Button>
		</div>
	{:else if result?.outcome === 'failed' && recovery === 'retry-order'}
		<Alert tone="danger">
			{#snippet children()}
				<p>
					<strong
						>Your order could not be completed (status {result.status}), so it was cancelled and any
						charge was refunded.</strong
					>
				</p>
				<p>
					No subscription was activated. You can place the order again - your registration and
					identity document are kept, so you will not be registered twice - or start over.
				</p>
			{/snippet}
			{#snippet actions()}
				<Button size="sm" onclick={onRetryOrder}>Place the order again</Button>
				<Button variant="ghost" size="sm" onclick={onStartOver}>Start over</Button>
			{/snippet}
		</Alert>
	{:else if result?.timedOut}
		<Alert tone="warning" role="status">
			<p>
				Your order is still processing (last status {result.status || 'pending'}). We will finish
				activation shortly - check your account in a moment.
			</p>
		</Alert>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}

	.pending,
	.done {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: var(--space-3);
		padding: var(--space-8) var(--space-4);
	}

	.pending-title {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 600;
	}

	.pending-detail,
	.detail {
		font-size: var(--text-sm-size);
		color: var(--color-text-muted);
	}

	.mono {
		font-family: var(--font-mono);
		overflow-wrap: anywhere;
	}

	.check {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 3.5rem;
		height: 3.5rem;
		border-radius: var(--radius-full);
		background: var(--color-success-bg);
	}

	.check svg {
		width: 1.75rem;
		height: 1.75rem;
		fill: none;
		stroke: var(--color-success-solid);
		stroke-width: 2.5;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	h2 {
		font-size: var(--text-xl-size);
		line-height: var(--text-xl-lh);
		font-weight: 700;
	}

	.done :global(.btn) {
		margin-top: var(--space-3);
	}
</style>
