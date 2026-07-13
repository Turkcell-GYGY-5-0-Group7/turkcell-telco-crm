<script lang="ts">
	// Step 5: payment (16.4.2). The order is already placed (PENDING_PAYMENT); the
	// page's primary action charges via the gateway (POST /api/v1/payments) with a
	// generated Idempotency-Key. Payment errors surface here; the TRUE activation
	// outcome is decided by polling on the next step, not by this call's response.
	import type { OnboardingOrderResult } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		order,
		total,
		currency,
		submitting,
		error
	}: {
		order: OnboardingOrderResult | null;
		total: number;
		currency: string;
		submitting: boolean;
		error: string;
	} = $props();
</script>

<div class="step-body">
	<h2>Payment</h2>
	<p class="hint">Mock PSP charge for the MVP - no real card details are collected.</p>

	<dl class="summary">
		<dt>Order</dt>
		<dd>{order?.orderId ?? '-'}</dd>
		<dt>Amount due</dt>
		<dd><strong>{formatMoney(total, currency)}</strong></dd>
	</dl>

	{#if submitting}
		<p class="hint">Processing payment...</p>
	{/if}

	{#if error}
		<p class="error" role="alert">{error}</p>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: 1rem;
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

	.summary {
		display: grid;
		grid-template-columns: max-content 1fr;
		gap: 0.4rem 1rem;
		margin: 0;
		font-size: 0.95rem;
	}

	dt {
		color: #6b7280;
	}

	dd {
		margin: 0;
		color: #1f2937;
	}

	.error {
		margin: 0;
		color: #b91c1c;
		font-size: 0.9rem;
	}
</style>
