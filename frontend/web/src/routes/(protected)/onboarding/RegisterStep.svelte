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
	<h2>Your details</h2>
	<p class="hint">
		We register you as an individual customer before placing the order. Your identity number is
		verified against the official TCKN checksum.
	</p>

	<label>
		First name
		<input type="text" bind:value={form.firstName} autocomplete="given-name" />
	</label>

	<label>
		Last name
		<input type="text" bind:value={form.lastName} autocomplete="family-name" />
	</label>

	<label>
		Identity number (TCKN)
		<input
			type="text"
			bind:value={form.identityNumber}
			inputmode="numeric"
			maxlength="11"
			autocomplete="off"
			aria-invalid={tcknError ? 'true' : undefined}
		/>
	</label>
	{#if tcknError}
		<p class="error" role="alert">{tcknError}</p>
	{/if}

	<label>
		Date of birth
		<input type="date" bind:value={form.dateOfBirth} autocomplete="bday" />
	</label>
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

	.error {
		margin: -0.5rem 0 0;
		color: #b91c1c;
		font-size: 0.85rem;
	}

	label {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		font-size: 0.9rem;
		color: #374151;
	}

	input {
		font: inherit;
		padding: 0.5rem 0.6rem;
		border: 1px solid #d1d5db;
		border-radius: 0.375rem;
	}
</style>
