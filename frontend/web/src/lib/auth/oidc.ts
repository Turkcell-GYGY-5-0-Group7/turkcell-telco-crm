// Keycloak Authorization Code + PKCE integration for the Telco CRM web app.
//
// ADR-011 / ADR-022, subtask 16.3.1. The BROWSER performs PKCE directly against
// the `telco-web` PUBLIC client (no client secret ever reaches the frontend);
// the web-bff only relays the resulting bearer to the gateway. This module owns:
//   - login redirect (signinRedirect)
//   - the redirect callback exchange (signinRedirectCallback)
//   - silent renewal via the refresh-token grant (automaticSilentRenew), so the
//     session outlives the 3600s access-token lifespan without a full redirect
//   - RP-initiated logout (signoutRedirect) that ends the Keycloak SSO session
//
// Tokens live in sessionStorage (oidc-client-ts user store) and the access token
// is mirrored into an in-memory cache (token-store.ts); localStorage is never
// used, to limit XSS token exfiltration (ADR-011).

import { browser } from '$app/environment';
import { goto } from '$app/navigation';
import { env } from '$env/dynamic/public';
import {
	UserManager,
	WebStorageStateStore,
	type User,
	type UserManagerSettings
} from 'oidc-client-ts';
import { writable, type Readable } from 'svelte/store';
import {
	setAccessTokenProvider,
	setAuthRedirectHandler,
	setRenewAccessTokenHandler
} from '$lib/api/client';
import { clearAccessToken, getAccessToken, setAccessToken } from './token-store';
import { loginUrlFor, safeReturnTo } from './route-guard';

/** Browser-facing Keycloak realm authority (OIDC discovery base). */
const DEFAULT_AUTHORITY = 'http://localhost:8085/realms/telco-crm';
/** The public SPA client registered in the `telco-crm` realm. */
const DEFAULT_CLIENT_ID = 'telco-web';

/** Reactive auth state for the UI (Sign in / Sign out affordance). */
export interface AuthState {
	/** `unknown` until the initial session probe resolves. */
	status: 'unknown' | 'authenticated' | 'anonymous';
	/** Preferred display name of the signed-in user, when available. */
	username: string | null;
}

const authStore = writable<AuthState>({ status: 'unknown', username: null });

/** Subscribe to reactive auth state (read-only). */
export const authState: Readable<AuthState> = { subscribe: authStore.subscribe };

let manager: UserManager | null = null;
let eventsWired = false;

/**
 * Build the oidc-client-ts settings from env with dev-friendly defaults. All of
 * authority, client id, redirect uri, and post-logout uri are env-overridable
 * (`PUBLIC_OIDC_*`); the redirect origin defaults to the current browser origin
 * (http://localhost:3000 in dev), which matches the `telco-web` redirect URI
 * `http://localhost:3000/*`.
 */
function buildSettings(): UserManagerSettings {
	const origin = window.location.origin;
	const authority = env.PUBLIC_OIDC_AUTHORITY?.trim() || DEFAULT_AUTHORITY;
	const clientId = env.PUBLIC_OIDC_CLIENT_ID?.trim() || DEFAULT_CLIENT_ID;
	const redirectUri = env.PUBLIC_OIDC_REDIRECT_URI?.trim() || `${origin}/auth/callback`;
	const postLogoutUri = env.PUBLIC_OIDC_POST_LOGOUT_REDIRECT_URI?.trim() || `${origin}/login`;

	return {
		authority,
		client_id: clientId,
		redirect_uri: redirectUri,
		post_logout_redirect_uri: postLogoutUri,
		// Authorization Code flow; oidc-client-ts adds PKCE (S256) automatically.
		response_type: 'code',
		scope: 'openid profile email',
		// Silent renewal uses the rotating refresh token grant (no hidden iframe,
		// so no separate silent-redirect page is required). Keycloak realm rotation
		// (revokeRefreshToken + refreshTokenMaxReuse:0) is honoured transparently.
		automaticSilentRenew: true,
		// Keep the token set out of localStorage; sessionStorage is cleared on tab close.
		userStore: new WebStorageStateStore({ store: sessionStorage }),
		stateStore: new WebStorageStateStore({ store: sessionStorage }),
		// Session status iframe monitoring is unnecessary for this SPA and avoids
		// third-party-cookie friction in dev.
		monitorSession: false
	};
}

function displayName(user: User | null): string | null {
	const profile = user?.profile;
	if (!profile) return null;
	return (
		(profile.preferred_username as string | undefined) ??
		profile.name ??
		(profile.email as string | undefined) ??
		null
	);
}

/** Reflect a loaded (or cleared) user into the token cache and reactive state. */
function applyUser(user: User | null): void {
	if (user && !user.expired) {
		setAccessToken(user.access_token);
		authStore.set({ status: 'authenticated', username: displayName(user) });
	} else {
		clearAccessToken();
		authStore.set({ status: 'anonymous', username: null });
	}
}

/** Lazily construct the singleton UserManager (browser only). */
function getManager(): UserManager {
	if (!manager) {
		manager = new UserManager(buildSettings());
		if (!eventsWired) {
			manager.events.addUserLoaded((user) => applyUser(user));
			manager.events.addUserUnloaded(() => applyUser(null));
			manager.events.addAccessTokenExpired(() => {
				// Fallback if automatic renewal did not fire; drop to anonymous on failure.
				manager?.signinSilent().catch(() => applyUser(null));
			});
			manager.events.addSilentRenewError(() => {
				// Renewal failed (e.g. SSO session ended); surface as needing re-login.
				applyUser(null);
			});
			eventsWired = true;
		}
	}
	return manager;
}

/**
 * Initialise auth on the client: register the bearer-token seam with the BFF
 * client and probe any existing session (e.g. after a reload). Safe to call
 * from a layout `onMount`; a no-op during SSR.
 */
export async function initAuth(): Promise<void> {
	if (!browser) return;
	// Feed the in-memory access token into the single BFF client seam.
	setAccessTokenProvider(getAccessToken);
	// Graceful 401 handling (16.3.3): let the single BFF client recover an
	// expired session via silent renewal, and fall back to a /login redirect
	// (return-to preserved) instead of ever surfacing a raw 401 to the user.
	setRenewAccessTokenHandler(renewSession);
	setAuthRedirectHandler(redirectToLogin);
	const user = await getManager().getUser();
	applyUser(user);
}

/**
 * Silent-renew handler for the BFF client's 401 recovery (16.3.3). Attempts the
 * oidc-client-ts refresh-token grant; on success the fresh access token is
 * mirrored into the token cache (so the retried request carries it) and `true`
 * is returned. Any failure drops to anonymous and returns `false`.
 */
async function renewSession(): Promise<boolean> {
	if (!browser) return false;
	try {
		const user = await getManager().signinSilent();
		if (user && !user.expired) {
			applyUser(user);
			return true;
		}
	} catch {
		// fall through to the anonymous outcome below
	}
	applyUser(null);
	return false;
}

/**
 * Auth-redirect handler for the BFF client's 401 recovery (16.3.3). Sends the
 * user to `/login`, preserving the current path as the post-login return target
 * (16.3.2). Browser-only; a no-op during SSR.
 */
function redirectToLogin(): void {
	if (!browser) return;
	const current = window.location.pathname + window.location.search;
	void goto(loginUrlFor(current));
}

/**
 * Resolve the current non-expired user for the session (browser only). Returns
 * `null` during SSR and when there is no valid session. The route guard
 * (16.3.2) awaits this so its decision does not race the initial session probe.
 */
export async function getActiveUser(): Promise<User | null> {
	if (!browser) return null;
	const user = await getManager().getUser();
	return user && !user.expired ? user : null;
}

/**
 * Begin Authorization Code + PKCE login by redirecting to Keycloak. The optional
 * `returnTo` (the originally requested route) is sanitised and carried through
 * Keycloak in the OIDC `state`, so the callback can return the user there
 * (16.3.2). This is the single login entry point - the guard and `/login` both
 * feed it rather than adding a parallel auth mechanism.
 */
export async function login(returnTo?: string): Promise<void> {
	await getManager().signinRedirect({ state: safeReturnTo(returnTo) });
}

/**
 * Complete the login by exchanging the authorization code at the callback route.
 * Returns the signed-in user so the callback page can route onward.
 */
export async function completeLogin(): Promise<User> {
	const user = await getManager().signinRedirectCallback();
	applyUser(user);
	return user;
}

/**
 * Extract the sanitised post-login return path from a completed user's OIDC
 * `state` (set by `login`). Always a safe local path; falls back to `/`.
 */
export function returnPathFromUser(user: User): string {
	return safeReturnTo(typeof user.state === 'string' ? user.state : undefined);
}

/**
 * End the session: clear local token state and trigger Keycloak RP-initiated
 * logout (end-session), which terminates the SSO session and redirects to the
 * post-logout URI.
 */
export async function logout(): Promise<void> {
	clearAccessToken();
	await getManager().signoutRedirect();
}
