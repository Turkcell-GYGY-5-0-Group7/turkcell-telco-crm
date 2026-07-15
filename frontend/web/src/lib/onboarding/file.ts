// KYC document capture: size/type policy + base64 encoding (16.4.2).
//
// The wizard sends the document INSIDE the JSON onboarding-order body
// (`POST /bff/v1/onboarding/order`), base64-encoded; web-bff decodes it and relays
// it as a MULTIPART upload to customer-service
// (`POST /api/v1/customers/{id}/documents`), which is where the real size limit lives.
//
// Without a client-side bound the user could attach a 5 MB phone photo, get half
// onboarded (the customer is registered BEFORE the document upload runs), and see a
// bare "Failed to fetch". So the limit is enforced HERE, before the user can leave the
// KYC step. This module is framework-agnostic and unit-tested in Node; the Svelte
// components are thin adapters over it (same pattern as ./identity.ts).
//
// LIMIT CHAIN (each hop is strictly looser than the one in front of it, so the user
// always meets the friendliest bound first):
//
//   browser raw file   <= 5 MiB   (MAX_KYC_FILE_BYTES, this module)
//   JSON to web-bff    ~= 6.7 MiB (base64 inflates by 4/3 - accounted for below)
//   web-bff decoded    <= 6 MiB   (telco.onboarding.kyc.max-document-bytes)
//   customer-service   <= 6 MB    (spring.servlet.multipart.max-file-size)
//
// 5 MiB comfortably covers a phone photo of an ID card or a 300 dpi passport scan while
// keeping the upload surface bounded (a bounded limit is a DoS control, not a nicety).

/**
 * Maximum RAW size of a KYC document the wizard will accept, in bytes (5 MiB).
 *
 * The value is chosen against the RAW file because that is what the user picks and what
 * customer-service ultimately stores: web-bff decodes the base64 back to these exact
 * bytes before building the multipart part. The base64 transport form is ~4/3 of this
 * ({@link base64EncodedSize}) and still fits every hop in the chain above.
 */
export const MAX_KYC_FILE_BYTES = 5 * 1024 * 1024;

/**
 * Document MIME types the KYC step accepts. Mirrors the `accept` attribute on the file
 * input, which is only a hint - a user can always pick "all files" in the OS dialog, so
 * the type is re-checked here.
 */
export const ALLOWED_KYC_MIME_TYPES = [
	'image/jpeg',
	'image/png',
	'image/heic',
	'image/heif',
	'image/webp',
	'application/pdf'
] as const;

/** Extensions used as a fallback when the browser reports no MIME type for the file. */
export const ALLOWED_KYC_EXTENSIONS = [
	'.jpg',
	'.jpeg',
	'.png',
	'.heic',
	'.heif',
	'.webp',
	'.pdf'
] as const;

/** Value for the file input's `accept` attribute, derived from the allow-list above. */
export const KYC_ACCEPT_ATTRIBUTE = [...ALLOWED_KYC_MIME_TYPES, ...ALLOWED_KYC_EXTENSIONS].join(
	','
);

/** Size in bytes of the base64 form of `byteLength` raw bytes (4 chars per 3 bytes, padded). */
export function base64EncodedSize(byteLength: number): number {
	return 4 * Math.ceil(byteLength / 3);
}

/** Human-readable byte size, e.g. `7.3 MB`, `812 KB`, `900 B`. */
export function formatBytes(bytes: number): string {
	if (bytes < 1024) {
		return `${bytes} B`;
	}
	const kb = bytes / 1024;
	if (kb < 1024) {
		return `${Math.round(kb)} KB`;
	}
	return `${(kb / 1024).toFixed(1)} MB`;
}

/** The subset of `File` this policy needs; keeps the rule testable without a DOM `File`. */
export interface KycFileFacts {
	name: string;
	size: number;
	type: string;
}

/** Outcome of {@link validateKycFile}: valid, or invalid with a message the UI shows verbatim. */
export type KycFileCheck = { valid: true } | { valid: false; message: string };

const VALID: KycFileCheck = { valid: true };

/**
 * The single source of truth for whether a picked KYC document may be submitted.
 *
 * Rejects (with a message naming the limit AND the file's actual size, so the user knows
 * how far over they are):
 * - an empty file (0 bytes - nothing to verify identity with);
 * - anything larger than {@link MAX_KYC_FILE_BYTES};
 * - a file whose type is not an image or a PDF.
 *
 * `maxBytes` is injectable for tests; production always uses the exported constant.
 */
export function validateKycFile(
	file: KycFileFacts | null | undefined,
	maxBytes: number = MAX_KYC_FILE_BYTES
): KycFileCheck {
	if (!file) {
		return { valid: false, message: 'Select an identity document to continue.' };
	}

	if (file.size <= 0) {
		return { valid: false, message: 'That file is empty. Select a readable identity document.' };
	}

	if (!isAllowedKycType(file)) {
		return {
			valid: false,
			message: 'Unsupported file type. Upload a JPEG, PNG, HEIC, WebP image or a PDF.'
		};
	}

	if (file.size > maxBytes) {
		return {
			valid: false,
			message:
				`That file is ${formatBytes(file.size)}, which is over the ${formatBytes(maxBytes)} limit ` +
				`for identity documents. Upload a smaller photo or scan (a phone photo can usually be ` +
				`re-taken at a lower resolution).`
		};
	}

	return VALID;
}

/** True when the file's MIME type is allowed - falling back to its extension when the browser reports none. */
export function isAllowedKycType(file: KycFileFacts): boolean {
	const mime = file.type.trim().toLowerCase();
	if (mime.length > 0) {
		return (ALLOWED_KYC_MIME_TYPES as readonly string[]).includes(mime);
	}
	const name = file.name.trim().toLowerCase();
	return (ALLOWED_KYC_EXTENSIONS as readonly string[]).some((extension) =>
		name.endsWith(extension)
	);
}

/** Read a browser File into a base64 string (no data-URL prefix). */
export async function readFileAsBase64(file: File): Promise<string> {
	const bytes = new Uint8Array(await file.arrayBuffer());
	let binary = '';
	for (const byte of bytes) {
		binary += String.fromCharCode(byte);
	}
	return btoa(binary);
}
