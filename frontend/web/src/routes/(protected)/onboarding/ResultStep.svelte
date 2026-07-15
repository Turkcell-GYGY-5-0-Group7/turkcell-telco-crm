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

	let {
		polling,
		liveStatus,
		attempts,
		result,
		error,
		recovery,
		onRetryOrder,
		onStartOver
	}: {
		polling: boolean;
		liveStatus: string;
		attempts: number;
		result: PollResult | null;
		error: string;
		recovery: RecoveryAction;
		onRetryOrder: () => void;
		onStartOver: () => void;
	} = $props();
</script>

<div class="step-body">
	<h2>Activation</h2>

	{#if error}
		<p class="error" role="alert">We could not confirm your activation: {error}</p>
		<p class="hint">
			Your order may still be processing - check your account before ordering again.
		</p>
		<div class="recovery-actions">
			<button type="button" class="secondary" onclick={onStartOver}>Start over</button>
		</div>
	{:else if polling}
		<p class="hint">
			Confirming activation (status: {liveStatus || 'pending'}, attempt {attempts})...
		</p>
	{:else if result?.outcome === 'activated'}
		<p class="success" role="status">Your subscription is active. Welcome aboard!</p>
		<p class="hint">Order {result.orderId} - status {result.status}.</p>
		<!--
			The session was silently renewed on activation (see the page), so the token
			behind this link now carries the `customerId` claim and the dashboard loads
			the real account instead of the BFF's unlinked 403.
		-->
		<div class="recovery-actions">
			<a class="primary" href="/">Go to my dashboard</a>
		</div>
	{:else if result?.outcome === 'failed' && recovery === 'retry-order'}
		<p class="error" role="alert">
			Your order could not be completed (status {result.status}), so it was cancelled and any charge
			was refunded.
		</p>
		<p class="hint">
			No subscription was activated. You can place the order again - your registration and identity
			document are kept, so you will not be registered twice - or start over.
		</p>
		<div class="recovery-actions">
			<button type="button" class="primary" onclick={onRetryOrder}>Place the order again</button>
			<button type="button" class="secondary" onclick={onStartOver}>Start over</button>
		</div>
	{:else if result?.timedOut}
		<p class="hint" role="status">
			Your order is still processing (last status {result.status || 'pending'}). We will finish
			activation shortly - check your account in a moment.
		</p>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	h2 {
		margin: 0;
		font-size: 1.15rem;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.success {
		margin: 0;
		color: #16a34a;
		font-weight: 600;
	}

	.error {
		margin: 0;
		color: #b91c1c;
		font-size: 0.95rem;
	}

	.recovery-actions {
		display: flex;
		gap: 0.75rem;
		margin-top: 0.25rem;
	}

	button {
		font: inherit;
		padding: 0.5rem 1.1rem;
		border-radius: 0.375rem;
		cursor: pointer;
	}

	.primary {
		border: 1px solid #16213e;
		background: #16213e;
		color: #ffffff;
	}

	a.primary {
		display: inline-block;
		font: inherit;
		font-weight: 600;
		padding: 0.5rem 1.1rem;
		border-radius: 0.375rem;
		text-decoration: none;
	}

	.secondary {
		border: 1px solid #d1d5db;
		background: #ffffff;
		color: #374151;
	}
</style>
