// Toast queue rules.
//
// Pure and framework-agnostic (unit tested in Node): the list operations are
// immutable functions here, and the runes wrapper in toast.svelte.ts only owns the
// reactive reference and the timers. That keeps the interesting behaviour - the
// queue cap, the per-kind lifetimes - testable without a DOM.
//
// A toast is for a TRANSIENT acknowledgement of something the user just did (a PDF
// downloaded, a download failed). Anything that describes the state of the page
// itself - a failed load, a rejected order - belongs in an Alert, which stays put.

/** What happened. Drives the toast's colour and its icon. */
export type ToastKind = 'success' | 'error' | 'info';

/** A queued toast. */
export interface ToastItem {
	/** Monotonic per-session id; the keyed-each identity and the dismiss handle. */
	id: number;
	kind: ToastKind;
	message: string;
	/** Auto-dismiss delay. 0 means the toast stays until dismissed. */
	timeoutMs: number;
}

/** Most toasts on screen at once. Beyond this the oldest is dropped. */
export const MAX_TOASTS = 4;

const DEFAULT_TIMEOUTS: Record<ToastKind, number> = {
	// An error is read, not glanced at, so it lingers longer than an acknowledgement.
	success: 4000,
	info: 4000,
	error: 7000
};

/** Build a toast, defaulting its lifetime from its kind. */
export function createToast(
	id: number,
	kind: ToastKind,
	message: string,
	timeoutMs?: number
): ToastItem {
	return {
		id,
		kind,
		message,
		timeoutMs: timeoutMs ?? DEFAULT_TIMEOUTS[kind]
	};
}

/**
 * Append a toast, capping the queue at `max` by dropping the OLDEST - a burst of
 * failures must not push the newest (and most relevant) message off screen.
 */
export function addToast(
	list: ToastItem[],
	item: ToastItem,
	max: number = MAX_TOASTS
): ToastItem[] {
	const next = [...list, item];
	return next.length > max ? next.slice(next.length - max) : next;
}

/** Remove one toast by id. Returns a new list; unknown ids are a no-op. */
export function removeToast(list: ToastItem[], id: number): ToastItem[] {
	return list.filter((item) => item.id !== id);
}
