// Base64 encoding for the captured KYC document (16.4.2).
//
// Runs in the browser only (invoked from a change handler on a file input); the
// bytes are base64-encoded so the document can ride the single JSON onboarding
// order call to the BFF, which owns the multipart upload to customer-service.

/** Read a browser File into a base64 string (no data-URL prefix). */
export async function readFileAsBase64(file: File): Promise<string> {
	const bytes = new Uint8Array(await file.arrayBuffer());
	let binary = '';
	for (const byte of bytes) {
		binary += String.fromCharCode(byte);
	}
	return btoa(binary);
}
