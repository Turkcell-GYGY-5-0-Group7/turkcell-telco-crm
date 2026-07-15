import { describe, expect, it } from 'vitest';
import { invoicePdfFilename } from './invoice';

describe('invoicePdfFilename', () => {
	it('derives a safe filename from the billing period', () => {
		expect(invoicePdfFilename({ invoiceId: 'inv-1', period: '2026-06' })).toBe(
			'invoice-2026-06.pdf'
		);
	});

	it('falls back to the invoice id when the period is blank', () => {
		expect(invoicePdfFilename({ invoiceId: 'inv-42', period: '' })).toBe('invoice-inv-42.pdf');
	});

	it('collapses unsafe characters and trims stray hyphens', () => {
		expect(invoicePdfFilename({ invoiceId: 'x', period: 'June / 2026 ' })).toBe(
			'invoice-June-2026.pdf'
		);
	});

	it('yields a well-formed name even with no usable source', () => {
		expect(invoicePdfFilename({ invoiceId: '', period: '' })).toBe('invoice-download.pdf');
	});
});
