<script lang="ts">
	// Step 1: customer registration (16.4.2). Binds directly into the shared
	// reactive form object owned by the wizard page; validation policy lives in
	// the framework-agnostic wizard model, not here.
	//
	// The fields are exactly the BFF's `CustomerRegistration` record (type,
	// firstName, lastName, identityNumber, dateOfBirth), which it relays verbatim to
	// customer-service. No email/phone is collected: the register contract has no
	// such fields, so asking for them would be a lie to the user.
	import { isValidTckn } from '$lib/onboarding/identity';
	import type { RegistrationForm } from '$lib/onboarding/wizard';

	let { form }: { form: RegistrationForm } = $props();

	// Surfaced only once something has been typed, so the field is not red on arrival.
	const tcknError = $derived(
		form.identityNumber.trim().length > 0 && !isValidTckn(form.identityNumber)
			? 'This is not a valid TCKN. It must be 11 digits with a valid checksum.'
			: ''
	);
</script>

<div class="step-body">
	<header>
		<h2>Your details</h2>
		<p class="hint">
			We register you as an individual customer before placing the order. Your identity number is
			verified against the official TCKN checksum.
		</p>
	</header>

	<div class="grid">
		<label class="field">
			<span class="field-label">First name</span>
			<input type="text" bind:value={form.firstName} autocomplete="given-name" />
		</label>

		<label class="field">
			<span class="field-label">Last name</span>
			<input type="text" bind:value={form.lastName} autocomplete="family-name" />
		</label>
	</div>

	<label class="field">
		<span class="field-label">Identity number (TCKN)</span>
		<input
			type="text"
			class="mono"
			bind:value={form.identityNumber}
			inputmode="numeric"
			maxlength="11"
			autocomplete="off"
			aria-invalid={tcknError ? 'true' : undefined}
		/>
	</label>
	{#if tcknError}
		<p class="error" role="alert">
			<svg viewBox="0 0 24 24" aria-hidden="true">
				<circle cx="12" cy="12" r="9" />
				<path d="M12 7.5v5M12 16v.5" />
			</svg>
			{tcknError}
		</p>
	{/if}

	<label class="field dob">
		<span class="field-label">Date of birth</span>
		<input type="date" bind:value={form.dateOfBirth} autocomplete="bday" />
	</label>
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

	.grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-4);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	.field-label {
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text-secondary);
	}

	input {
		font: inherit;
		font-size: var(--text-sm-size);
		padding: 0.6rem 0.75rem;
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		transition: border-color var(--duration-fast) var(--ease-out);
	}

	input:hover {
		border-color: var(--color-text-muted);
	}

	input[aria-invalid='true'] {
		border-color: var(--color-danger-solid);
	}

	.mono {
		font-family: var(--font-mono);
		letter-spacing: 0.06em;
	}

	.dob {
		max-width: 14rem;
	}

	.error {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin-top: calc(-1 * var(--space-3));
		color: var(--color-danger-text);
		font-size: var(--text-sm-size);
	}

	.error svg {
		width: 1rem;
		height: 1rem;
		flex-shrink: 0;
		fill: none;
		stroke: currentColor;
		stroke-width: 1.8;
		stroke-linecap: round;
	}

	@media (max-width: 40rem) {
		.grid {
			grid-template-columns: 1fr;
		}
	}
</style>
