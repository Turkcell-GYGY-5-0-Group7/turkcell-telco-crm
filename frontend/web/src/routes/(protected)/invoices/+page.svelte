<script lang="ts">
	// Invoices view (16.5.2): a PAGED invoice list from GET /bff/v1/invoices
	// (ApiClient.getInvoices), where each "Download PDF" retrieves the ACTUAL PDF
	// through the authenticated single client and triggers a real browser download.
	//
	// How the authenticated download works (no placeholder link):
	//   1. api.downloadInvoicePdf(invoice.pdfUrl) GETs the gateway PDF route with
	//      the caller's bearer attached (a plain <a href> would omit it -> 401),
	//      returning the PDF as a Blob through the one HTTP path.
	//   2. triggerBlobDownload turns that Blob into an object URL + anchor click,
	//      so the browser saves it under a safe per-invoice filename.
	// Browser-only APIs (URL.createObjectURL, anchor click) are reached only inside
	// the click handler via $lib/account/download; this page's group is ssr=false.
	//
	// Loading, error and empty states are handled honestly; the read is scoped
	// server-side to the caller (16.5.1) and the client sends no id, only page/size.
	import { onMount } from 'svelte';
	import { ApiError, api, type InvoiceList, type InvoiceSummary } from '$lib/api/client';
	import { triggerBlobDownload } from '$lib/account/download';
	import { invoicePdfFilename } from '$lib/account/invoice';
	import InvoiceRow from '$lib/account/InvoiceRow.svelte';

	const PAGE_SIZE = 10;

	let result = $state<InvoiceList | null>(null);
	let page = $state(0);
	let loading = $state(true);
	let error = $state('');

	// Per-invoice download state, keyed by invoiceId, so one row's spinner/error
	// never blocks another's download button.
	let downloadingId = $state<string | null>(null);
	let downloadErrors = $state<Record<string, string>>({});

	const totalPages = $derived(result?.totalPages ?? 0);
	const canPrev = $derived(page > 0 && !loading);
	const canNext = $derived(result !== null && page < totalPages - 1 && !loading);

	onMount(() => {
		void load(0);
	});

	async function load(target: number) {
		loading = true;
		error = '';
		try {
			result = await api.getInvoices(target, PAGE_SIZE);
			page = result.page;
		} catch (err) {
			error =
				err instanceof ApiError
					? `Could not load your invoices. (HTTP ${err.status})`
					: 'Could not load your invoices.';
		} finally {
			loading = false;
		}
	}

	function goPrev() {
		if (canPrev) void load(page - 1);
	}

	function goNext() {
		if (canNext) void load(page + 1);
	}

	async function download(invoice: InvoiceSummary) {
		if (downloadingId) return;
		downloadingId = invoice.invoiceId;
		downloadErrors = { ...downloadErrors, [invoice.invoiceId]: '' };
		try {
			const blob = await api.downloadInvoicePdf(invoice.pdfUrl);
			triggerBlobDownload(blob, invoicePdfFilename(invoice));
		} catch (err) {
			const message =
				err instanceof ApiError ? `Download failed. (HTTP ${err.status})` : 'Download failed.';
			downloadErrors = { ...downloadErrors, [invoice.invoiceId]: message };
		} finally {
			downloadingId = null;
		}
	}
</script>

<section class="page">
	<h1>Invoices</h1>

	{#if loading}
		<p class="hint">Loading your invoices...</p>
	{:else if error}
		<div class="notice error" role="alert">
			<p>{error}</p>
			<button type="button" onclick={() => load(page)}>Retry</button>
		</div>
	{:else if result && result.invoices.length > 0}
		<table>
			<thead>
				<tr>
					<th>Period</th>
					<th>Amount</th>
					<th>Status</th>
					<th></th>
				</tr>
			</thead>
			<tbody>
				{#each result.invoices as invoice (invoice.invoiceId)}
					<InvoiceRow
						{invoice}
						downloading={downloadingId === invoice.invoiceId}
						error={downloadErrors[invoice.invoiceId] ?? ''}
						onDownload={() => download(invoice)}
					/>
				{/each}
			</tbody>
		</table>

		<div class="pager">
			<button type="button" onclick={goPrev} disabled={!canPrev}>Previous</button>
			<span class="page-label">Page {page + 1} of {Math.max(totalPages, 1)}</span>
			<button type="button" onclick={goNext} disabled={!canNext}>Next</button>
		</div>
	{:else}
		<p class="hint">You have no invoices yet.</p>
	{/if}
</section>

<style>
	.page {
		max-width: 44rem;
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	h1 {
		margin: 0;
		font-size: 1.5rem;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		background: #ffffff;
		border: 1px solid #e5e7eb;
		border-radius: 0.75rem;
		overflow: hidden;
	}

	th {
		text-align: left;
		padding: 0.6rem 0.75rem;
		font-size: 0.75rem;
		text-transform: uppercase;
		letter-spacing: 0.03em;
		color: #6b7280;
		background: #f9fafb;
		border-bottom: 1px solid #e5e7eb;
	}

	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 1rem;
	}

	.page-label {
		font-size: 0.85rem;
		color: #4b5563;
	}

	.pager button {
		font: inherit;
		font-size: 0.85rem;
		padding: 0.35rem 0.9rem;
		border-radius: 0.375rem;
		border: 1px solid #d1d5db;
		background: #ffffff;
		color: #374151;
		cursor: pointer;
	}

	.pager button:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.hint {
		margin: 0;
		color: #6b7280;
		font-size: 0.9rem;
	}

	.notice {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1rem 1.25rem;
		border-radius: 0.5rem;
	}

	.notice.error {
		background: #fef2f2;
		border: 1px solid #fecaca;
	}

	.notice.error p {
		margin: 0;
		color: #b91c1c;
		font-size: 0.9rem;
	}

	.notice button {
		font: inherit;
		font-size: 0.85rem;
		padding: 0.35rem 0.9rem;
		border-radius: 0.375rem;
		border: 1px solid #b91c1c;
		background: #ffffff;
		color: #b91c1c;
		cursor: pointer;
	}
</style>
