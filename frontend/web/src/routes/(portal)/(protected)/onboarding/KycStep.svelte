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
	//
	// The drop-zone styling is a <label> wrapping the NATIVE file input - the input is
	// what the user actually operates, so keyboard and screen-reader behaviour is the
	// browser's, not a re-implementation.
	import type { KycDocumentType } from '$lib/api/client';
	import {
		KYC_ACCEPT_ATTRIBUTE,
		MAX_KYC_FILE_BYTES,
		formatBytes,
		validateKycFile
	} from '$lib/onboarding/file';
	import Alert from '$lib/ui/Alert.svelte';

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
	<header>
		<h2>Identity verification</h2>
		<p class="hint">
			Upload a photo or scan of your ID document (JPEG, PNG, HEIC, WebP, or PDF), up to
			{formatBytes(MAX_KYC_FILE_BYTES)}.
		</p>
	</header>

	<label class="field">
		<span class="field-label">Document type</span>
		<select value={documentType} onchange={onTypeChange}>
			<option value="ID_CARD">Identity card</option>
			<option value="PASSPORT">Passport</option>
		</select>
	</label>

	<div class="field">
		<span class="field-label">Identity document</span>
		<label class="dropzone">
			<svg class="upload" viewBox="0 0 24 24" aria-hidden="true">
				<path d="M12 16V5M7.5 9.5L12 5l4.5 4.5" />
				<path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
			</svg>
			<span class="dropzone-title">Choose a file</span>
			<span class="dropzone-hint">JPEG, PNG, HEIC, WebP or PDF</span>
			<input type="file" accept={KYC_ACCEPT_ATTRIBUTE} onchange={onChange} />
		</label>
	</div>

	{#if error}
		<Alert tone="danger">
			<p>{error}</p>
		</Alert>
	{:else if file}
		<p class="selected">
			<svg viewBox="0 0 24 24" aria-hidden="true">
				<path d="M6 3h9l3 3v15H6z" />
				<path d="M9 13.5l2 2 4-4" />
			</svg>
			<span class="filename">{file.name}</span>
			<span class="filesize">{formatBytes(file.size)}</span>
		</p>
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

	select {
		font: inherit;
		font-size: var(--text-sm-size);
		padding: 0.6rem 0.75rem;
		max-width: 16rem;
		color: var(--color-text);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
	}

	.dropzone {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-8) var(--space-6);
		text-align: center;
		border: 2px dashed var(--color-border-strong);
		border-radius: var(--radius-lg);
		background: var(--color-surface-alt);
		cursor: pointer;
		transition:
			border-color var(--duration-fast) var(--ease-out),
			background-color var(--duration-fast) var(--ease-out);
	}

	.dropzone:hover {
		border-color: var(--color-accent);
		background: var(--color-accent-soft);
	}

	/* The native input still does the work; it is simply not drawn. */
	.dropzone input {
		position: absolute;
		width: 1px;
		height: 1px;
		opacity: 0;
	}

	.dropzone:focus-within {
		outline: 2px solid var(--color-focus);
		outline-offset: 2px;
	}

	.upload {
		width: 1.75rem;
		height: 1.75rem;
		margin-bottom: var(--space-2);
		fill: none;
		stroke: var(--color-text-muted);
		stroke-width: 1.8;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	.dropzone-title {
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text);
	}

	.dropzone-hint {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.selected {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-alt);
		font-size: var(--text-sm-size);
	}

	.selected svg {
		width: 1.15rem;
		height: 1.15rem;
		flex-shrink: 0;
		fill: none;
		stroke: var(--color-success-solid);
		stroke-width: 1.8;
		stroke-linecap: round;
		stroke-linejoin: round;
	}

	.filename {
		font-weight: 600;
		overflow-wrap: anywhere;
	}

	.filesize {
		margin-left: auto;
		color: var(--color-text-muted);
		white-space: nowrap;
	}
</style>
