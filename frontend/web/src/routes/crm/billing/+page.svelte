<script lang="ts">
	// CRM billing: look up an invoice by id, view its breakdown, and download the PDF
	// through the authenticated client (a plain link would omit the bearer). ADMIN-only
	// in this console. Refunds are event-driven and ADMIN-gated on payment-service with
	// no safe browser entry point, so "Refund" is an honest coming-soon action.
	import { page } from '$app/stores';
	import { ApiError, api, type InvoiceDetail } from '$lib/api/client';
	import { triggerBlobDownload } from '$lib/account/download';
	import RoleGate from '$lib/crm/RoleGate.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import Input from '$lib/ui/Input.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import { toasts } from '$lib/ui/toast.svelte';

	const ALLOW = ['ADMIN'];
	const roles = $derived(($page.data.roles as string[] | undefined) ?? []);

	let lookupId = $state('');
	let invoice = $state<InvoiceDetail | null>(null);
	let loading = $state(false);
	let downloading = $state(false);
	let error = $state('');
	let notFound = $state(false);

	async function lookup() {
		const id = lookupId.trim();
		if (!id) return;
		loading = true;
		error = '';
		notFound = false;
		invoice = null;
		try {
			invoice = await api.getInvoiceById(id);
		} catch (err) {
			if (err instanceof ApiError && (err.status === 404 || err.status === 403)) notFound = true;
			else
				error = err instanceof ApiError ? `Lookup failed. (HTTP ${err.status})` : 'Lookup failed.';
		} finally {
			loading = false;
		}
	}

	async function download() {
		if (!invoice) return;
		downloading = true;
		try {
			const blob = await api.downloadInvoicePdf(`/api/v1/invoices/${invoice.id}/pdf`);
			const filename = `invoice-${invoice.id.slice(0, 8)}.pdf`;
			triggerBlobDownload(blob, filename);
			toasts.success(`Saved ${filename}`);
		} catch (err) {
			toasts.error(
				err instanceof ApiError ? `Download failed. (HTTP ${err.status})` : 'Download failed.'
			);
		} finally {
			downloading = false;
		}
	}

	function refund() {
		toasts.info('Refunds are not available from the console yet - this is a planned capability.');
	}

	function fmtMoney(amount: number, currency: string): string {
		return `${amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
	}
</script>

<section class="page">
	<PageHeader title="Billing" subtitle="Find an invoice and download its PDF." />

	<RoleGate {roles} allow={ALLOW}>
		{#snippet children()}
			<Card>
				<div class="lookup">
					<Input
						id="inv-lookup"
						label="Invoice id"
						bind:value={lookupId}
						placeholder="UUID"
						hint="Find an invoice id via Customers -> open a customer -> Invoices."
					/>
					<Button variant="secondary" size="sm" {loading} onclick={lookup}>Look up</Button>
				</div>
			</Card>

			{#if error}
				<Alert tone="danger">{#snippet children()}<p>{error}</p>{/snippet}</Alert>
			{:else if notFound}
				<Alert tone="warning"
					>{#snippet children()}<p>No invoice was found for that id.</p>{/snippet}</Alert
				>
			{:else if invoice}
				{@const inv = invoice}
				<Card>
					<div class="head">
						<div>
							<h2 class="mono">{inv.id}</h2>
							<span class="muted mono">Customer {inv.customerId}</span>
						</div>
						<Badge tone="neutral">{#snippet children()}{inv.status}{/snippet}</Badge>
					</div>
					<div class="meta">
						<div class="field">
							<span class="k">Subtotal</span><span class="v tabular"
								>{fmtMoney(inv.subTotal, inv.currency)}</span
							>
						</div>
						<div class="field">
							<span class="k">Tax</span><span class="v tabular"
								>{fmtMoney(inv.tax, inv.currency)}</span
							>
						</div>
						<div class="field">
							<span class="k">Total</span><span class="v tabular"
								>{fmtMoney(inv.grandTotal, inv.currency)}</span
							>
						</div>
						<div class="field"><span class="k">Due</span><span class="v">{inv.dueDate}</span></div>
					</div>

					{#if inv.lines && inv.lines.length > 0}
						<div class="lines">
							<h3>Line items</h3>
							<ul>
								{#each inv.lines as line, i (i)}
									<li>
										<span>{line.description}</span>
										<span class="tabular">{fmtMoney(line.amount, inv.currency)}</span>
									</li>
								{/each}
							</ul>
						</div>
					{/if}

					<div class="actions">
						<Button variant="secondary" size="sm" loading={downloading} onclick={download}>
							Download PDF
						</Button>
						<Button variant="ghost" size="sm" onclick={refund}>Refund</Button>
					</div>
				</Card>
			{/if}
		{/snippet}
	</RoleGate>
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
	}

	.lookup {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: end;
		gap: var(--space-3);
		max-width: 34rem;
	}

	.head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-4);
		margin-bottom: var(--space-5);
	}

	.head h2 {
		font-size: var(--text-lg-size);
		font-weight: 700;
	}

	.meta {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
		gap: var(--space-4);
	}

	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.k {
		font-size: var(--text-xs-size);
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
	}

	.v {
		font-size: var(--text-sm-size);
	}

	.mono {
		font-family: var(--font-mono);
		overflow-wrap: anywhere;
	}

	.muted {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}

	.lines {
		margin-top: var(--space-5);
	}

	.lines h3 {
		margin-bottom: var(--space-3);
		font-size: var(--text-sm-size);
		font-weight: 700;
	}

	.lines ul {
		list-style: none;
	}

	.lines li {
		display: flex;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-2) 0;
		font-size: var(--text-sm-size);
		border-bottom: 1px solid var(--color-border);
	}

	.actions {
		display: flex;
		gap: var(--space-3);
		margin-top: var(--space-5);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}
</style>
