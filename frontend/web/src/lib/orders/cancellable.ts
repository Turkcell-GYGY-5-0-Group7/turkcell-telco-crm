// Pure order-state helper (unit tested in Node).
//
// order-service only allows cancelling an order that is still PENDING or CONFIRMED;
// any later state (FULFILLED, CANCELLED, FAILED) is terminal. The UI mirrors that
// rule to decide whether to OFFER the cancel action, but never relies on it for
// correctness - the server re-checks and returns a business-rule message the page
// surfaces if the client's view is stale.

const CANCELLABLE = new Set(['PENDING', 'CONFIRMED']);

/** True when an order in this status may still be cancelled. */
export function isCancellable(status: string): boolean {
	return CANCELLABLE.has((status ?? '').trim().toUpperCase());
}
