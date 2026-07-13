// Browser blob-download helper for the invoice PDF (16.5.2).
//
// The authenticated PDF bytes arrive as a Blob from the single BFF client
// (ApiClient.downloadInvoicePdf, which sends the bearer token the gateway PDF
// route requires). This module turns that Blob into a real browser download via
// an object URL + a synthesized anchor click - it is NEVER a placeholder link.
//
// Browser-only APIs (URL.createObjectURL, document, anchor click) are reached
// ONLY through an injected port, resolved LAZILY when the download is triggered.
// Importing this module is therefore SSR-safe, and the port is injectable so the
// framework-agnostic behavior is unit-tested in Node (no jsdom).

/** The minimal anchor surface the download uses. An HTMLAnchorElement satisfies it. */
export interface DownloadAnchor {
	href: string;
	download: string;
	click(): void;
}

/** Injectable browser surface for {@link triggerBlobDownload}. */
export interface DownloadPort {
	createObjectURL(blob: Blob): string;
	revokeObjectURL(url: string): void;
	createAnchor(): DownloadAnchor;
}

/**
 * The default port, bound to real browser globals. Constructed lazily (only when a
 * download is actually triggered) so this module imports cleanly under SSR/Node,
 * where `URL.createObjectURL`/`document` do not exist.
 */
export function browserDownloadPort(): DownloadPort {
	return {
		createObjectURL: (blob) => URL.createObjectURL(blob),
		revokeObjectURL: (url) => URL.revokeObjectURL(url),
		createAnchor: () => document.createElement('a')
	};
}

/**
 * Trigger a browser download of `blob` under `filename`. Creates an object URL,
 * clicks a hidden anchor bound to it, and always revokes the URL afterwards (even
 * if the click throws) so no object URL is leaked.
 */
export function triggerBlobDownload(
	blob: Blob,
	filename: string,
	port: DownloadPort = browserDownloadPort()
): void {
	const url = port.createObjectURL(blob);
	try {
		const anchor = port.createAnchor();
		anchor.href = url;
		anchor.download = filename;
		anchor.click();
	} finally {
		port.revokeObjectURL(url);
	}
}
