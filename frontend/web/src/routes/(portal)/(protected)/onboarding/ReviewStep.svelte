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
	import Alert from '$lib/ui/Alert.svelte';

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
	<header>
		<h2>Review your order</h2>
		<p class="hint">
			Placing the order starts your activation: we charge the first month and activate your line
			automatically, and this wizard follows the real order status until it is done.
		</p>
	</header>

	<dl class="summary">
		<div class="row">
			<dt>Customer</dt>
			<dd>{form.firstName} {form.lastName}</dd>
		</div>
		<div class="row">
			<dt>Identity number</dt>
			<dd class="mono">{form.identityNumber}</dd>
		</div>
		<div class="row">
			<dt>Date of birth</dt>
			<dd>{form.dateOfBirth || '-'}</dd>
		</div>
		<div class="row">
			<dt>KYC document</dt>
			<dd>{filename ? `${documentLabel} - ${filename}` : 'None'}</dd>
		</div>
		<div class="row">
			<dt>Tariff</dt>
			<dd>
				{tariff ? `${tariff.name} (${formatMoney(tariff.monthlyPrice, tariff.currency)}/mo)` : '-'}
			</dd>
		</div>
		<div class="row">
			<dt>Add-ons</dt>
			<dd>
				{#if addons.length > 0}
					{addons.map((addon) => addon.name).join(', ')}
				{:else}
					None
				{/if}
			</dd>
		</div>
		<div class="row total">
			<dt>Monthly total</dt>
			<dd class="tabular"><strong>{formatMoney(total, currency)}</strong></dd>
		</div>
	</dl>

	{#if reusingCustomer}
		<Alert tone="info" role="status">
			<p>
				You are already registered, so this places a new order for your existing customer record -
				you will not be registered twice.
			</p>
		</Alert>
	{/if}

	{#if placing}
		<p class="hint" role="status">Placing your order...</p>
	{/if}

	{#if error}
		<Alert tone="danger">
			<p>{error}</p>
		</Alert>
	{/if}
</div>

<style>
	.step-body {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	header {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	h2 {
		font-size: var(--text-lg-size);
		line-height: var(--text-lg-lh);
		font-weight: 700;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--text-sm-size);
		line-height: var(--text-sm-lh);
	}

	.summary {
		margin: 0;
		display: flex;
		flex-direction: column;
	}

	.row {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--space-6);
		padding: var(--space-3) 0;
		border-bottom: 1px solid var(--color-border);
		font-size: var(--text-sm-size);
	}

	dt {
		color: var(--color-text-muted);
		flex-shrink: 0;
	}

	dd {
		margin: 0;
		text-align: right;
		color: var(--color-text);
		font-weight: 500;
		overflow-wrap: anywhere;
	}

	.mono {
		font-family: var(--font-mono);
	}

	.row.total {
		margin-top: var(--space-2);
		padding-top: var(--space-4);
		border-top: 2px solid var(--color-border-strong);
		border-bottom: 0;
		font-size: var(--text-lg-size);
	}

	.row.total dt {
		color: var(--color-text);
		font-weight: 600;
	}
</style>
