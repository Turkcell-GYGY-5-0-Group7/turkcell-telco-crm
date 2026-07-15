<script lang="ts">
	// One invoice on the /invoices view (16.5.2): period, amount, status, and a
	// real "Download PDF" action. The click delegates to the page's onDownload,
	// which fetches the authenticated PDF Blob through the single BFF client and
	// triggers a browser download - this is NOT a placeholder link.
	//
	// Per-row `downloading` state is owned by the page and passed in. The download's
	// OUTCOME is not rendered here: success and failure are transient acknowledgements
	// of an action the user just took, so the page raises them as toasts rather than
	// wedging a message into a table cell.
	import type { InvoiceSummary } from '$lib/api/client';
	import { formatMoney } from '$lib/onboarding/money';
	import { invoiceStatusTone } from '$lib/home/summary';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import { invoiceToneToBadge } from '$lib/ui/status';

	let {
		invoice,
		downloading,
		onDownload
	}: {
		invoice: InvoiceSummary;
		downloading: boolean;
		onDownload: () => void;
	} = $props();
</script>

<tr>
	<td class="period">{invoice.period}</td>
	<td class="amount tabular">{formatMoney(invoice.amount, invoice.currency)}</td>
	<td>
		<Badge tone={invoiceToneToBadge(invoiceStatusTone(invoice.status))}>{invoice.status}</Badge>
	</td>
	<td class="action">
		<Button variant="secondary" size="sm" loading={downloading} onclick={onDownload}>
			<svg viewBox="0 0 24 24" aria-hidden="true">
				<path d="M12 4v11M7.5 11l4.5 4.5 4.5-4.5M5 19h14" />
			</svg>
			Download PDF
		</Button>
	</td>
</tr>

<style>
	td {
		padding: var(--space-3) var(--space-4);
		border-bottom: 1px solid var(--color-border);
		font-size: var(--text-sm-size);
		vertical-align: middle;
	}

	.period {
		font-weight: 600;
	}

	.amount {
		text-align: right;
	}

	.action {
		text-align: right;
		white-space: nowrap;
	}

	svg {
		width: 0.9rem;
		height: 0.9rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
