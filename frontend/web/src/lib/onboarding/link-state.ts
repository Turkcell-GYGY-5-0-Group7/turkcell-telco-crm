// Identity-to-customer LINK STATE: the first-run state of an authenticated user
// who has not completed onboarding yet (16.5 defect fix).
//
// The state, precisely: a user who signed up and signed in holds a token with
// roles [SUBSCRIBER] but `customerId: null`, because identity-service mints that
// claim only AFTER it consumes `customer.registered.v1` - an event that does not
// exist until onboarding registers the customer. web-bff's self-scoping guard
// (16.5.1) therefore CORRECTLY rejects every account read for that identity:
//
//   GET /bff/v1/home | /bff/v1/account | /bff/v1/invoices -> 403
//   { "success": false, "error": { "code": "ACCESS_DENIED",
//     "message": "authenticated identity is not linked to a customer record" } }
//
// That is the MOST COMMON first-run state, not a fault: the honest UI is a
// welcome + "start onboarding" call to action, never a red error. This module is
// the SINGLE place that knows how to recognise it, so the three pages that read
// account data (/, /account, /invoices) share one predicate instead of each
// duplicating a magic 403 check. It is pure/injectable and unit tested in Node.
//
// Detection rests on the HTTP STATUS, not on the message text: on these three BFF
// reads a 403 can only come from the self-scoping guard. An expired or invalid
// bearer is a 401 (which the API client already recovers via silent renew, then a
// /login redirect), and the endpoints carry no finer-grained authorization than
// "is this identity linked to a customer". Matching the server's English sentence
// instead would be brittle - a reworded message would silently reintroduce the
// red-error defect. Every OTHER failure (500, 502, a network error, a non-403
// status) is passed through untouched and still shown as a real error.

import { ApiError } from '$lib/api/client';

/**
 * True when `error` is the BFF's "authenticated identity is not linked to a
 * customer record" rejection of an account read - i.e. the user is signed in but
 * has not completed onboarding yet.
 *
 * Only meaningful around the self-scoped BFF account reads (home / account /
 * invoices); that scoping is what makes a bare 403 unambiguous. Anything that is
 * not an {@link ApiError} with status 403 (a 500, a 502, a thrown TypeError from
 * a network failure, a non-Error value) is NOT this state and must surface as a
 * genuine error.
 */
export function isCustomerUnlinkedError(error: unknown): boolean {
	return error instanceof ApiError && error.status === 403;
}

/** Outcome of an account read that may legitimately find no linked customer. */
export type LinkedLoad<T> =
	{ state: 'loaded'; data: T } | { state: 'unlinked' } | { state: 'error'; error: unknown };

export interface LinkedLoadOptions {
	/**
	 * Silent-renew seam (the oidc-client-ts refresh-token grant registered in
	 * `$lib/auth/oidc.ts`). Resolves `true` when a FRESH access token is now in
	 * play. Supplied to close the STALE-TOKEN race: the `customerId` claim is
	 * minted asynchronously, so the token a just-onboarded user is holding can
	 * still carry `customerId: null` even though the customer now exists. When
	 * omitted, an unlinked read is reported as-is (no renewal attempted).
	 */
	renewSession?: () => boolean | Promise<boolean>;
}

/**
 * Run a self-scoped BFF account read and classify the result into the three states
 * the UI actually has: loaded, `unlinked` (onboarding not completed - a legitimate
 * application state), or a genuine `error`.
 *
 * On an unlinked read, and only then, ONE silent renew is attempted (when a
 * `renewSession` seam is supplied) and the read is retried EXACTLY ONCE with the
 * refreshed token. This is the honest fix for the stale-token race, and it cannot
 * loop: if the renew fails, or the refreshed token still carries no `customerId`
 * (a second 403), the caller gets `unlinked` and shows the onboarding CTA. A renew
 * that throws is treated as a failed renew, never as a load error. Errors raised by
 * the RETRY are classified exactly like the first attempt, so a 500 on the retry is
 * still reported as a real error.
 */
export async function loadLinkedResource<T>(
	read: () => Promise<T>,
	options: LinkedLoadOptions = {}
): Promise<LinkedLoad<T>> {
	const first = await attempt(read);
	if (first.state !== 'unlinked' || !options.renewSession) {
		return first;
	}

	if (!(await renewQuietly(options.renewSession))) {
		return first;
	}

	return attempt(read);
}

/**
 * Whether a completed onboarding run means the session's token is now STALE and
 * must be refreshed before the next account read.
 *
 * True only for the `activated` terminal outcome: the customer exists and
 * identity-service has minted (or is about to mint) the `customerId` claim, yet the
 * token in hand was issued before that. A `failed` or still-`pending` outcome
 * registers no new link worth refreshing for, so the wizard does not spend a
 * refresh-token grant on it.
 */
export function shouldRefreshSessionAfterOnboarding(
	result: { outcome: string } | null | undefined
): boolean {
	return result?.outcome === 'activated';
}

async function attempt<T>(read: () => Promise<T>): Promise<LinkedLoad<T>> {
	try {
		return { state: 'loaded', data: await read() };
	} catch (error) {
		return isCustomerUnlinkedError(error) ? { state: 'unlinked' } : { state: 'error', error };
	}
}

async function renewQuietly(renew: () => boolean | Promise<boolean>): Promise<boolean> {
	try {
		return await renew();
	} catch {
		return false;
	}
}
