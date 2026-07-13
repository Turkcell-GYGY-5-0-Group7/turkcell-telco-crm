<script lang="ts">
	// Step 1: customer registration (16.4.2). Binds directly into the shared
	// reactive form object owned by the wizard page; validation policy lives in
	// the framework-agnostic wizard model, not here.
	import type { RegistrationForm } from '$lib/onboarding/wizard';

	let { form }: { form: RegistrationForm } = $props();
</script>

<div class="step-body">
	<h2>Your details</h2>
	<p class="hint">We register you as a customer before placing the order.</p>

	<label>
		Full name
		<input type="text" bind:value={form.fullName} autocomplete="name" />
	</label>

	<label>
		National ID (TCKN / VKN)
		<input type="text" bind:value={form.nationalId} inputmode="numeric" autocomplete="off" />
	</label>

	<label>
		Email
		<input type="email" bind:value={form.email} autocomplete="email" />
	</label>

	<label>
		Phone number
		<input type="tel" bind:value={form.phoneNumber} autocomplete="tel" />
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
