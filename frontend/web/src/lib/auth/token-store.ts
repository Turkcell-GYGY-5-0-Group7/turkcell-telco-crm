// In-memory access-token holder for the authenticated browser session.
//
// ADR-011 / ADR-022 (subtask 16.3.1): the access token is held only in memory
// (never in localStorage) to limit XSS exposure. The oidc-client-ts UserManager
// owns the full token set in sessionStorage; this tiny store caches just the
// current access token so the synchronous BFF client seam
// (`client.ts#getAccessToken`) can attach the bearer without touching browser
// APIs. Keeping it framework-agnostic makes the read/clear behaviour unit
// testable without a live Keycloak or a DOM.

let accessToken: string | null = null;

/** Return the current in-memory access token, or null when signed out. */
export function getAccessToken(): string | null {
	return accessToken;
}

/** Cache the current access token (called on login and each silent renewal). */
export function setAccessToken(token: string | null): void {
	accessToken = token && token.length > 0 ? token : null;
}

/** Clear the cached access token (called on logout / expiry). */
export function clearAccessToken(): void {
	accessToken = null;
}
