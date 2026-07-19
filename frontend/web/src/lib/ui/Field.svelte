<script lang="ts">
	// Form-control chrome: a label, optional hint, and an error line, wired to the
	// control by id so `aria-describedby` and `aria-invalid` are correct without each
	// page re-implementing the plumbing. The control itself is the `children` snippet
	// (an Input/Select/Textarea or a raw element), so Field owns only the surrounding
	// accessibility structure.
	import type { Snippet } from 'svelte';

	let {
		id,
		label,
		hint,
		error,
		required = false,
		children
	}: {
		/** Id of the control inside; label's `for` and describedby ids derive from it. */
		id: string;
		label: string;
		hint?: string;
		error?: string;
		required?: boolean;
		children: Snippet;
	} = $props();
</script>

<div class="field">
	<label for={id}>
		{label}
		{#if required}<span class="req" aria-hidden="true">*</span>{/if}
	</label>
	{#if hint && !error}
		<p class="hint" id={`${id}-hint`}>{hint}</p>
	{/if}
	{@render children()}
	{#if error}
		<p class="error" id={`${id}-error`} role="alert">{error}</p>
	{/if}
</div>

<style>
	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}

	label {
		font-size: var(--text-sm-size);
		font-weight: 600;
		color: var(--color-text-secondary);
	}

	.req {
		color: var(--color-danger-solid);
		margin-left: 0.15rem;
	}

	.hint {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.error {
		font-size: var(--text-xs-size);
		color: var(--color-danger-text);
	}
</style>
