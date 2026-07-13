<script lang="ts">
	// Step 4: order review + placement (16.4.2). Read-only summary of everything
	// captured so far; the page's primary action places the order (with a
	// generated Idempotency-Key) via the BFF. Placement errors surface here.
	import type { Addon, Tariff } from '$lib/api/client';
	import type { RegistrationForm } from '$lib/onboarding/wizard';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		form,
		filename,
		tariff,
		addons,
		total,
		currency,
		placing,
		error
	}: {
		form: RegistrationForm;
		filename: string;
		tariff: Tariff | null;
		addons: Addon[];
		total: number;
		currency: string;
		placing: boolean;
		error: string;
	} = $props();
</script>

<div class="step-body">
	<h2>Review your order</h2>

	<dl class="summary">
		<dt>Customer</dt>
		<dd>{form.fullName} - {form.email} - {form.phoneNumber}</dd>

		<dt>KYC document</dt>
		<dd>{filename || 'None'}</dd>

		<dt>Tariff</dt>
		<dd>
			{tariff ? `${tariff.name} (${formatMoney(tariff.monthlyPrice, tariff.currency)}/mo)` : '-'}
		</dd>

		<dt>Add-ons</dt>
		<dd>
			{#if addons.length > 0}
				{addons.map((addon) => addon.name).join(', ')}
			{:else}
				None
			{/if}
		</dd>

		<dt>Monthly total</dt>
		<dd><strong>{formatMoney(total, currency)}</strong></dd>
	</dl>

	{#if placing}
		<p class="hint">Placing your order...</p>
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

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.error {
		margin: 0;
		color: #b91c1c;
		font-size: 0.9rem;
	}
</style>
