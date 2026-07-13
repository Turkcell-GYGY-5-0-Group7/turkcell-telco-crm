<script lang="ts">
	// Step 6: activation result + failure/compensation recovery (16.4.2 / 16.4.3).
	// This step shows the TRUE saga outcome sourced from polling
	// GET /api/v1/orders/{id}, never an optimistic assumption. On failure it does
	// not dead-end: the recovery policy ($lib/onboarding/recovery) decides an
	// honest, actionable path - a payment retry on the same order (fresh
	// idempotency key) for a compensated/cancelled order, or a route back to the
	// KYC step for an identity rejection. The buttons call back into the page,
	// which owns the step transition and BFF client (no parallel flow).
	import type { PollResult } from '$lib/onboarding/order-status';
	import type { RecoveryAction } from '$lib/onboarding/recovery';

	let {
		polling,
		liveStatus,
		attempts,
		result,
		error,
		recovery,
		onRetryPayment,
		onRestartKyc,
		onStartOver
	}: {
		polling: boolean;
		liveStatus: string;
		attempts: number;
		result: PollResult | null;
		error: string;
		recovery: RecoveryAction;
		onRetryPayment: () => void;
		onRestartKyc: () => void;
		onStartOver: () => void;
	} = $props();
</script>

<div class="step-body">
	<h2>Activation</h2>

	{#if error}
		<p class="error" role="alert">We could not confirm your activation: {error}</p>
		<div class="recovery-actions">
			<button type="button" class="primary" onclick={onRetryPayment}>Retry payment</button>
			<button type="button" class="secondary" onclick={onStartOver}>Start over</button>
		</div>
	{:else if polling}
		<p class="hint">
			Confirming activation (status: {liveStatus || 'pending'}, attempt {attempts})...
		</p>
	{:else if result?.outcome === 'activated'}
		<p class="success" role="status">Your subscription is active. Welcome aboard!</p>
		<p class="hint">Order {result.orderId} - status {result.status}.</p>
	{:else if result?.outcome === 'failed' && recovery === 'restart-kyc'}
		<p class="error" role="alert">
			Identity verification was not approved, so your order could not be activated.
		</p>
		<p class="hint">
			Please re-check your details and upload a clear identity document to try again. The plan you
			chose has been kept.
		</p>
		<div class="recovery-actions">
			<button type="button" class="primary" onclick={onRestartKyc}>Re-upload identity</button>
			<button type="button" class="secondary" onclick={onStartOver}>Start over</button>
		</div>
	{:else if result?.outcome === 'failed'}
		<p class="error" role="alert">
			Payment failed, so your order was cancelled and any charge was refunded.
		</p>
		<p class="hint">
			No subscription was activated. You can retry the payment for this order - a new payment
			reference is used so it is charged fresh - or start over.
		</p>
		<div class="recovery-actions">
			<button type="button" class="primary" onclick={onRetryPayment}>Retry payment</button>
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

	.secondary {
		border: 1px solid #d1d5db;
		background: #ffffff;
		color: #374151;
	}
</style>
