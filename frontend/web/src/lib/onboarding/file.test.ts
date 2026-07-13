import { describe, expect, it } from 'vitest';
import {
	ALLOWED_KYC_MIME_TYPES,
	KYC_ACCEPT_ATTRIBUTE,
	MAX_KYC_FILE_BYTES,
	base64EncodedSize,
	formatBytes,
	isAllowedKycType,
	validateKycFile,
	type KycFileFacts
} from './file';

const MIB = 1024 * 1024;

/**
 * The decoded-byte ceiling web-bff enforces before it builds the multipart upload
 * (`telco.onboarding.kyc.max-document-bytes` in microservices/configs/web-bff/application.yml),
 * and the multipart ceiling customer-service enforces
 * (`spring.servlet.multipart.max-file-size` in microservices/configs/customer-service/application.yml).
 * The frontend limit MUST stay at or below both, otherwise the wizard would happily send a file the
 * server rejects - the exact defect this module exists to prevent.
 */
const WEB_BFF_MAX_DECODED_BYTES = 6 * MIB;
const CUSTOMER_SERVICE_MAX_FILE_BYTES = 6 * MIB;

function file(overrides: Partial<KycFileFacts> = {}): KycFileFacts {
	return { name: 'id.jpg', size: 512 * 1024, type: 'image/jpeg', ...overrides };
}

describe('validateKycFile', () => {
	it('accepts a typical ID photo under the limit', () => {
		expect(validateKycFile(file({ size: 700 * 1024 }))).toEqual({ valid: true });
	});

	it('accepts a file exactly at the limit', () => {
		expect(validateKycFile(file({ size: MAX_KYC_FILE_BYTES }))).toEqual({ valid: true });
	});

	it('rejects a file one byte over the limit', () => {
		const result = validateKycFile(file({ size: MAX_KYC_FILE_BYTES + 1 }));
		expect(result.valid).toBe(false);
	});

	it('rejects a typical oversize phone photo, naming the limit and the actual size', () => {
		// 7.4 MB screenshot / phone photo: the case that produced HTTP 503 DEPENDENCY_FAILURE live.
		const result = validateKycFile(file({ size: 7_800_000 }));

		expect(result.valid).toBe(false);
		if (result.valid) return;
		expect(result.message).toContain('7.4 MB'); // the file's ACTUAL size
		expect(result.message).toContain('5.0 MB'); // the limit
	});

	it('rejects an empty file', () => {
		const result = validateKycFile(file({ size: 0 }));
		expect(result.valid).toBe(false);
		if (result.valid) return;
		expect(result.message).toContain('empty');
	});

	it('rejects a missing file', () => {
		expect(validateKycFile(null).valid).toBe(false);
	});

	it('rejects an unsupported file type before checking its size', () => {
		const result = validateKycFile(file({ name: 'clip.mp4', size: 10, type: 'video/mp4' }));
		expect(result.valid).toBe(false);
		if (result.valid) return;
		expect(result.message).toContain('Unsupported file type');
	});

	it('honours an injected limit (used to prove the base64 chain, not shipped)', () => {
		expect(validateKycFile(file({ size: 2048 }), 1024).valid).toBe(false);
		expect(validateKycFile(file({ size: 1024 }), 1024).valid).toBe(true);
	});
});

describe('base64 inflation is accounted for', () => {
	it('computes the encoded size as 4 bytes per 3 raw bytes', () => {
		expect(base64EncodedSize(3)).toBe(4);
		expect(base64EncodedSize(1)).toBe(4); // padded
		expect(base64EncodedSize(6)).toBe(8);
	});

	it('inflates the raw limit by ~4/3 on the wire', () => {
		const encoded = base64EncodedSize(MAX_KYC_FILE_BYTES);
		expect(encoded).toBeGreaterThan(MAX_KYC_FILE_BYTES);
		expect(encoded).toBeCloseTo((MAX_KYC_FILE_BYTES * 4) / 3, -1);
	});

	it('keeps the DECODED bytes (what web-bff and customer-service actually see) inside both server limits', () => {
		// web-bff base64-decodes the content before forwarding, so the server-side bound applies to the
		// RAW size - which is exactly what this module caps. Guard the ordering of the limit chain.
		expect(MAX_KYC_FILE_BYTES).toBeLessThanOrEqual(WEB_BFF_MAX_DECODED_BYTES);
		expect(MAX_KYC_FILE_BYTES).toBeLessThanOrEqual(CUSTOMER_SERVICE_MAX_FILE_BYTES);
	});
});

describe('accepted document types', () => {
	it('accepts images and PDFs by MIME type', () => {
		for (const type of ALLOWED_KYC_MIME_TYPES) {
			expect(isAllowedKycType(file({ type }))).toBe(true);
		}
	});

	it('falls back to the extension when the browser reports no MIME type', () => {
		expect(isAllowedKycType(file({ name: 'passport.PDF', type: '' }))).toBe(true);
		expect(isAllowedKycType(file({ name: 'archive.zip', type: '' }))).toBe(false);
	});

	it('exposes an accept attribute covering every allowed type', () => {
		expect(KYC_ACCEPT_ATTRIBUTE).toContain('image/jpeg');
		expect(KYC_ACCEPT_ATTRIBUTE).toContain('application/pdf');
		expect(KYC_ACCEPT_ATTRIBUTE).not.toContain('image/*');
	});
});

describe('formatBytes', () => {
	it('renders bytes, kilobytes and megabytes', () => {
		expect(formatBytes(900)).toBe('900 B');
		expect(formatBytes(700 * 1024)).toBe('700 KB');
		expect(formatBytes(5 * MIB)).toBe('5.0 MB');
	});
});
