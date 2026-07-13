<script lang="ts">
	// Step 2: KYC document upload (16.4.2). Captures the document TYPE and a single
	// identity document; the page reads its bytes to base64 only when placing the
	// order, so no browser API runs here at module load.
	//
	// The type is required: the BFF forwards it as the `type` multipart part to
	// customer-service, which binds it to its `DocumentType` enum (ID_CARD | PASSPORT).
	// Guessing it in the UI would fail the upload, so the user chooses it.
	//
	// The document must also pass the size/type policy BEFORE the user can continue:
	// customer-service caps the multipart upload, and that upload runs only AFTER the
	// customer has been registered - so an oversize file used to leave the user half
	// onboarded with an unintelligible network error. The rule itself lives in
	// $lib/onboarding/file.ts (framework-agnostic, unit tested); this component only
	// renders its verdict.
	import type { KycDocumentType } from '$lib/api/client';
	import {
		KYC_ACCEPT_ATTRIBUTE,
		MAX_KYC_FILE_BYTES,
		formatBytes,
		validateKycFile
	} from '$lib/onboarding/file';

	let {
		documentType,
		file,
		onTypeSelected,
		onFileSelected
	}: {
		documentType: KycDocumentType;
		file: File | null;
		onTypeSelected: (type: KycDocumentType) => void;
		onFileSelected: (file: File | null) => void;
	} = $props();

	// Only complain once the user has actually picked something; an untouched step shows
	// the limit as a hint, not as an error.
	const check = $derived(file ? validateKycFile(file) : null);
	const error = $derived(check && !check.valid ? check.message : '');

	function onChange(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		onFileSelected(input.files?.[0] ?? null);
	}

	function onTypeChange(event: Event) {
		const select = event.currentTarget as HTMLSelectElement;
		onTypeSelected(select.value as KycDocumentType);
	}
</script>

<div class="step-body">
	<h2>Identity verification</h2>
	<p class="hint">
		Upload a photo or scan of your ID document (JPEG, PNG, HEIC, WebP, or PDF), up to
		{formatBytes(MAX_KYC_FILE_BYTES)}.
	</p>

	<label class="field">
		Document type
		<select value={documentType} onchange={onTypeChange}>
			<option value="ID_CARD">Identity card</option>
			<option value="PASSPORT">Passport</option>
		</select>
	</label>

	<label class="field">
		Identity document
		<input type="file" accept={KYC_ACCEPT_ATTRIBUTE} onchange={onChange} />
	</label>

	{#if error}
		<p class="error" role="alert">{error}</p>
	{:else if file}
		<p class="selected">
			Selected: <strong>{file.name}</strong> ({formatBytes(file.size)})
		</p>
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

	.field {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		font-size: 0.9rem;
		color: #374151;
	}

	select {
		font: inherit;
		padding: 0.5rem 0.6rem;
		border: 1px solid #d1d5db;
		border-radius: 0.375rem;
		background: #ffffff;
	}

	.selected {
		margin: 0;
		font-size: 0.9rem;
		color: #16a34a;
	}

	.error {
		margin: 0;
		font-size: 0.9rem;
		color: #b91c1c;
		background: #fef2f2;
		border: 1px solid #fecaca;
		border-radius: 0.375rem;
		padding: 0.6rem 0.75rem;
	}
</style>
