import { describe, expect, it } from 'vitest';
import { listTickets, rememberTicket, type StorageLike } from './ticket-registry';

/** A minimal in-memory StorageLike, plus a throwing variant for error paths. */
function memStorage(
	seed: Record<string, string> = {}
): StorageLike & { data: Record<string, string> } {
	const data = { ...seed };
	return {
		data,
		getItem: (k) => (k in data ? data[k] : null),
		setItem: (k, v) => {
			data[k] = v;
		}
	};
}

describe('rememberTicket / listTickets', () => {
	it('remembers a ticket and lists it back', () => {
		const storage = memStorage();
		rememberTicket(
			storage,
			'cust-1',
			{ id: 't1', subject: 'No signal' },
			new Date('2026-07-01T00:00:00Z')
		);
		const list = listTickets(storage, 'cust-1');
		expect(list).toHaveLength(1);
		expect(list[0]).toMatchObject({ id: 't1', subject: 'No signal' });
	});

	it('scopes entries per customer', () => {
		const storage = memStorage();
		rememberTicket(storage, 'cust-1', { id: 't1', subject: 'A' });
		expect(listTickets(storage, 'cust-2')).toEqual([]);
	});

	it('orders newest first', () => {
		const storage = memStorage();
		rememberTicket(storage, 'c', { id: 't1', subject: 'old' }, new Date('2026-07-01T00:00:00Z'));
		rememberTicket(storage, 'c', { id: 't2', subject: 'new' }, new Date('2026-07-05T00:00:00Z'));
		expect(listTickets(storage, 'c').map((t) => t.id)).toEqual(['t2', 't1']);
	});

	it('dedupes by id, moving the repeat to the front', () => {
		const storage = memStorage();
		rememberTicket(storage, 'c', { id: 't1', subject: 'first' }, new Date('2026-07-01T00:00:00Z'));
		rememberTicket(storage, 'c', { id: 't2', subject: 'second' }, new Date('2026-07-02T00:00:00Z'));
		rememberTicket(
			storage,
			'c',
			{ id: 't1', subject: 'first again' },
			new Date('2026-07-03T00:00:00Z')
		);
		const list = listTickets(storage, 'c');
		expect(list.map((t) => t.id)).toEqual(['t1', 't2']);
		expect(list[0].subject).toBe('first again');
	});

	it('caps the list at 25 entries', () => {
		const storage = memStorage();
		for (let i = 0; i < 30; i++) {
			rememberTicket(storage, 'c', { id: `t${i}`, subject: `s${i}` }, new Date(2026, 0, 1, 0, i));
		}
		expect(listTickets(storage, 'c')).toHaveLength(25);
	});

	it('returns empty for malformed storage and missing customer', () => {
		expect(listTickets(memStorage({ 'telco.tickets.c': 'not json' }), 'c')).toEqual([]);
		expect(listTickets(memStorage(), '')).toEqual([]);
	});

	it('drops non-conforming entries', () => {
		const storage = memStorage({
			'telco.tickets.c': JSON.stringify([
				{ id: 't1', subject: 'ok', rememberedAt: '2026-01-01' },
				{ id: 5 }
			])
		});
		expect(listTickets(storage, 'c').map((t) => t.id)).toEqual(['t1']);
	});
});
