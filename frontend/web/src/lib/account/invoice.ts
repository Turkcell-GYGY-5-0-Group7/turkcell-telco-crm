// Invoice presentation helpers for the /invoices view (16.5.2).
//
// Pure and framework-agnostic (unit tested in Node). Derives a safe download
// filename for the invoice PDF from the invoice's period (falling back to its id),
// stripping anything that is not a filesystem-safe character so the browser's
// "Save as" name is always well-formed.

import type { InvoiceSummary } from '$lib/api/client';

/**
 * A stable, safe download filename for an invoice PDF, e.g. `invoice-2026-06.pdf`.
 * Uses the billing period when present, else the invoice id; non `[A-Za-z0-9._-]`
 * runs collapse to a single hyphen and leading/trailing hyphens are trimmed.
 */
export function invoicePdfFilename(invoice: Pick<InvoiceSummary, 'invoiceId' | 'period'>): string {
	const base = (invoice.period ?? '').trim() || (invoice.invoiceId ?? '').trim();
	const safe = base.replace(/[^A-Za-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '');
	return `invoice-${safe || 'download'}.pdf`;
}
