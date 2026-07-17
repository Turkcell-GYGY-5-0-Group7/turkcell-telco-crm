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
	// A download's outcome is a TOAST, not an inline message: it acknowledges an
	// action the user just took and belongs outside the table. A failed LIST load is
	// different - it is the state of the page - so that stays an Alert with a Retry.
	//
	// Loading, not-yet-onboarded, error and empty states are handled honestly; the
	// read is scoped server-side to the caller (16.5.1) and the client sends no id,
	// only page/size. A user who has not onboarded has no linked customer record, so
	// that self-scoping guard refuses the read with 403 - an expected first-run state,
	// classified by `loadLinkedResource` (with one silent renew + retry, for the token
	// that predates the customerId claim) and answered with an onboarding CTA rather
	// than a red HTTP error. Real failures still show as errors.
	import { onMount } from 'svelte';
	import { ApiError, api, type InvoiceList, type InvoiceSummary } from '$lib/api/client';
	import { renewSession } from '$lib/auth/oidc';
	import { loadLinkedResource } from '$lib/onboarding/link-state';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import { triggerBlobDownload } from '$lib/account/download';
	import { invoicePdfFilename } from '$lib/account/invoice';
	import InvoiceRow from '$lib/account/InvoiceRow.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import { toasts } from '$lib/ui/toast.svelte';

	const PAGE_SIZE = 10;

	let result = $state<InvoiceList | null>(null);
	let page = $state(0);
	let loading = $state(true);
	let error = $state('');
	let notOnboarded = $state(false);

	// Per-invoice download state, keyed by invoiceId, so one row's spinner never
	// blocks another's download button.
	let downloadingId = $state<string | null>(null);

	const totalPages = $derived(result?.totalPages ?? 0);
	const canPrev = $derived(page > 0 && !loading);
	const canNext = $derived(result !== null && page < totalPages - 1 && !loading);

	onMount(() => {
		void load(0);
	});

	async function load(target: number) {
		loading = true;
		error = '';
		notOnboarded = false;
		const loaded = await loadLinkedResource(() => api.getInvoices(target, PAGE_SIZE), {
			renewSession
		});
		if (loaded.state === 'loaded') {
			result = loaded.data;
			page = loaded.data.page;
		} else if (loaded.state === 'unlinked') {
			result = null;
			notOnboarded = true;
		} else {
			error =
				loaded.error instanceof ApiError
					? `Could not load your invoices. (HTTP ${loaded.error.status})`
					: 'Could not load your invoices.';
		}
		loading = false;
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
		try {
			const blob = await api.downloadInvoicePdf(invoice.pdfUrl);
			const filename = invoicePdfFilename(invoice);
			triggerBlobDownload(blob, filename);
			toasts.success(`Saved ${filename}`);
		} catch (err) {
			const message =
				err instanceof ApiError ? `Download failed. (HTTP ${err.status})` : 'Download failed.';
			toasts.error(message);
		} finally {
			downloadingId = null;
		}
	}
</script>

<section class="page">
	<PageHeader title="Invoices" subtitle="Your monthly billing history." />

	{#if loading}
		<Card padding="none">
			<div class="skeleton-table" aria-busy="true" aria-label="Loading your invoices">
				{#each [0, 1, 2, 3, 4] as index (index)}
					<div class="skeleton-row">
						<Skeleton variant="text" width="30%" />
						<Skeleton variant="text" width="20%" />
					</div>
				{/each}
			</div>
		</Card>
	{:else if error}
		<Alert tone="danger">
			{#snippet children()}
				<p>{error}</p>
			{/snippet}
			{#snippet actions()}
				<Button variant="secondary" size="sm" onclick={() => load(page)}>Retry</Button>
			{/snippet}
		</Alert>
	{:else if notOnboarded}
		<NotOnboardedNotice
			message="You have not completed onboarding yet, so no invoices have been issued to you. Your monthly invoices will be listed here once your subscription is activated."
		/>
	{:else if result && result.invoices.length > 0}
		<Card padding="none">
			<div class="table-scroll">
				<table>
					<thead>
						<tr>
							<th scope="col">Period</th>
							<th scope="col" class="right">Amount</th>
							<th scope="col">Status</th>
							<th scope="col"><span class="visually-hidden">Actions</span></th>
						</tr>
					</thead>
					<tbody>
						{#each result.invoices as invoice (invoice.invoiceId)}
							<InvoiceRow
								{invoice}
								downloading={downloadingId === invoice.invoiceId}
								onDownload={() => download(invoice)}
							/>
						{/each}
					</tbody>
				</table>
			</div>
		</Card>

		<div class="pager">
			<Button variant="secondary" size="sm" disabled={!canPrev} onclick={goPrev}>
				<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 5.5L8 12l6.5 6.5" /></svg>
				Previous
			</Button>
			<span class="page-label tabular">Page {page + 1} of {Math.max(totalPages, 1)}</span>
			<Button variant="secondary" size="sm" disabled={!canNext} onclick={goNext}>
				Next
				<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 5.5L16 12l-6.5 6.5" /></svg>
			</Button>
		</div>
	{:else}
		<EmptyState
			title="No invoices yet"
			message="Once your first billing period closes, your invoices will be listed here and you can download each one as a PDF."
		/>
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 56rem;
	}

	/* Narrow viewports scroll the table sideways rather than reflowing it into cards:
	   an invoice is a row of four short, comparable facts and reads better kept as one. */
	.table-scroll {
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: collapse;
	}

	th {
		text-align: left;
		padding: var(--space-3) var(--space-4);
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
		background: var(--color-surface-alt);
		border-bottom: 1px solid var(--color-border);
		white-space: nowrap;
	}

	th.right {
		text-align: right;
	}

	table :global(tbody tr:hover) {
		background: var(--color-surface-alt);
	}

	table :global(tbody tr:last-child td) {
		border-bottom: 0;
	}

	.skeleton-table {
		display: flex;
		flex-direction: column;
	}

	.skeleton-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	.skeleton-row:last-child {
		border-bottom: 0;
	}

	.pager {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-4);
	}

	.page-label {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
	}

	.pager svg {
		width: 0.9rem;
		height: 0.9rem;
		fill: none;
		stroke: currentColor;
		stroke-width: 2;
		stroke-linecap: round;
		stroke-linejoin: round;
	}
</style>
