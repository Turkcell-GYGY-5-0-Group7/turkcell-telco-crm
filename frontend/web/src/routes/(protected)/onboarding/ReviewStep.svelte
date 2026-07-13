<script lang="ts">
	// Step 4: order review + placement (16.4.2). Read-only summary of everything
	// captured so far; the page's primary action places the order (with a generated
	// Idempotency-Key) via the BFF. Placement errors - including customer-service's
	// verbatim rejection of the identity number - surface here.
	//
	// Placing the order is the wizard's ONLY write. The charge then happens
	// server-side off `order.created.v1` (TELCO-CRM-MVP Section 9.2), so this step is
	// followed directly by the polled activation result: there is no payment step.
	// On a retry after a failed saga the customer already exists, so the order is
	// re-placed through the BFF's `customerId` reuse path (no re-registration).
	import type { Addon, Tariff } from '$lib/api/client';
	import type { RegistrationForm } from '$lib/onboarding/wizard';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		form,
		documentLabel,
		filename,
		tariff,
		addons,
		total,
		currency,
		placing,
		reusingCustomer,
		error
	}: {
		form: RegistrationForm;
		documentLabel: string;
		filename: string;
		tariff: Tariff | null;
		addons: Addon[];
		total: number;
		currency: string;
		placing: boolean;
		reusingCustomer: boolean;
		error: string;
	} = $props();
</script>

<div class="step-body">
	<h2>Review your order</h2>

	<dl class="summary">
		<dt>Customer</dt>
		<dd>{form.firstName} {form.lastName}</dd>

		<dt>Identity number</dt>
		<dd>{form.identityNumber}</dd>

		<dt>Date of birth</dt>
		<dd>{form.dateOfBirth || '-'}</dd>

		<dt>KYC document</dt>
		<dd>{filename ? `${documentLabel} - ${filename}` : 'None'}</dd>

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

	<p class="hint">
		Placing the order starts your activation: we charge the first month and activate your line
		automatically, and this wizard follows the real order status until it is done.
	</p>

	{#if reusingCustomer}
		<p class="hint">
			You are already registered, so this places a new order for your existing customer record - you
			will not be registered twice.
		</p>
	{/if}

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
