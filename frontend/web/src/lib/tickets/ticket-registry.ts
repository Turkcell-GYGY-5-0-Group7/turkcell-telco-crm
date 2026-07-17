// A small client-side registry of tickets this browser has opened (unit tested in
// Node with an injected storage).
//
// Why this exists: ticket-service exposes open / get-by-id / comment, but NO
// "list my tickets" endpoint. So after a subscriber opens a ticket, the only way to
// find it again is by its id. Rather than lose it, the page remembers the ids it
// created in localStorage, keyed by the caller's customerId, and offers them back as
// a convenience list. This is a UI aid, not a source of truth: it is per-browser,
// clearing storage forgets the list (the ticket still exists and is reachable by id),
// and every entry is re-fetched from the server before it is shown.
//
// Storage is injected (`StorageLike`) so the parse/cap/dedupe rules are testable
// without a DOM and the page can pass `localStorage`.

export interface StorageLike {
	getItem(key: string): string | null;
	setItem(key: string, value: string): void;
}

export interface RememberedTicket {
	id: string;
	subject: string;
	/** ISO timestamp the id was remembered (for ordering newest-first). */
	rememberedAt: string;
}

/** Cap the list so a busy demo browser does not grow the entry unbounded. */
const MAX_ENTRIES = 25;

function keyFor(customerId: string): string {
	return `telco.tickets.${customerId}`;
}

/**
 * The tickets remembered for a customer, newest first. Never throws: malformed or
 * absent storage yields an empty list, and non-conforming entries are dropped.
 */
export function listTickets(storage: StorageLike, customerId: string): RememberedTicket[] {
	if (!customerId) return [];
	let raw: string | null;
	try {
		raw = storage.getItem(keyFor(customerId));
	} catch {
		return [];
	}
	if (!raw) return [];
	try {
		const parsed: unknown = JSON.parse(raw);
		if (!Array.isArray(parsed)) return [];
		return parsed
			.filter(
				(entry): entry is RememberedTicket =>
					typeof entry === 'object' &&
					entry !== null &&
					typeof (entry as RememberedTicket).id === 'string' &&
					typeof (entry as RememberedTicket).subject === 'string' &&
					typeof (entry as RememberedTicket).rememberedAt === 'string'
			)
			.sort((a, b) => b.rememberedAt.localeCompare(a.rememberedAt));
	} catch {
		return [];
	}
}

/**
 * Remember a newly opened ticket for a customer and return the updated list. An id
 * already present is moved to the front (deduped), and the list is capped at
 * {@link MAX_ENTRIES}. Storage write errors are swallowed - remembering is
 * best-effort and must never break the open-ticket flow.
 */
export function rememberTicket(
	storage: StorageLike,
	customerId: string,
	ticket: { id: string; subject: string },
	now: Date = new Date()
): RememberedTicket[] {
	if (!customerId || !ticket.id) return listTickets(storage, customerId);

	const existing = listTickets(storage, customerId).filter((entry) => entry.id !== ticket.id);
	const next: RememberedTicket[] = [
		{ id: ticket.id, subject: ticket.subject, rememberedAt: now.toISOString() },
		...existing
	].slice(0, MAX_ENTRIES);

	try {
		storage.setItem(keyFor(customerId), JSON.stringify(next));
	} catch {
		// best-effort: the ticket was still created server-side
	}
	return next;
}
