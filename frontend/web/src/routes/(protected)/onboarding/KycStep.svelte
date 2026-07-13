<script lang="ts">
	// Step 2: KYC document upload (16.4.2). Captures a single identity document;
	// the page reads its bytes to base64 only when advancing, so no browser API
	// runs here at module load. The document is uploaded via the BFF order call.

	let {
		filename,
		onFileSelected
	}: { filename: string; onFileSelected: (file: File | null) => void } = $props();

	function onChange(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		onFileSelected(input.files?.[0] ?? null);
	}
</script>

<div class="step-body">
	<h2>Identity verification</h2>
	<p class="hint">Upload a photo of your ID document (JPEG, PNG, or PDF).</p>

	<label class="file">
		Identity document
		<input type="file" accept="image/*,application/pdf" onchange={onChange} />
	</label>

	{#if filename}
		<p class="selected">Selected: <strong>{filename}</strong></p>
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

	.file {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		font-size: 0.9rem;
		color: #374151;
	}

	.selected {
		margin: 0;
		font-size: 0.9rem;
		color: #16a34a;
	}
</style>
