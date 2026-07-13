<script lang="ts">
	// One invoice on the /invoices view (16.5.2): period, amount, status, and a
	// real "Download PDF" action. The click delegates to the page's onDownload,
	// which fetches the authenticated PDF Blob through the single BFF client and
	// triggers a browser download - this is NOT a placeholder link. Per-row
	// `downloading`/`error` state is owned by the page and passed in.
	import type { InvoiceSummary } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';

	let {
		invoice,
		downloading,
		error,
		onDownload
	}: {
		invoice: InvoiceSummary;
		downloading: boolean;
		error: string;
		onDownload: () => void;
	} = $props();

	const statusClass = $derived(invoice.status.toLowerCase());
</script>

<tr>
	<td>{invoice.period}</td>
	<td class="amount">{formatMoney(invoice.amount, invoice.currency)}</td>
	<td><span class={`status ${statusClass}`}>{invoice.status}</span></td>
	<td class="action">
		<button type="button" onclick={onDownload} disabled={downloading}>
			{downloading ? 'Downloading...' : 'Download PDF'}
		</button>
		{#if error}
			<span class="error" role="alert">{error}</span>
		{/if}
	</td>
</tr>

<style>
	td {
		padding: 0.6rem 0.75rem;
		border-bottom: 1px solid #f0f1f3;
		font-size: 0.9rem;
		vertical-align: middle;
	}

	.amount {
		font-variant-numeric: tabular-nums;
	}

	.status {
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.03em;
		padding: 0.15rem 0.5rem;
		border-radius: 999px;
		background: #e5e7eb;
		color: #374151;
	}

	.status.paid {
		background: #dcfce7;
		color: #166534;
	}

	.status.overdue {
		background: #fee2e2;
		color: #991b1b;
	}

	.status.pending {
		background: #fef9c3;
		color: #854d0e;
	}

	.action {
		display: flex;
		align-items: center;
		gap: 0.6rem;
		white-space: nowrap;
	}

	button {
		font: inherit;
		font-size: 0.85rem;
		padding: 0.35rem 0.8rem;
		border-radius: 0.375rem;
		border: 1px solid #16213e;
		background: #16213e;
		color: #ffffff;
		cursor: pointer;
	}

	button:disabled {
		opacity: 0.6;
		cursor: default;
	}

	.error {
		color: #b91c1c;
		font-size: 0.8rem;
	}
</style>
