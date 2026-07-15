import { describe, expect, it } from 'vitest';
import { MAX_TOASTS, addToast, createToast, removeToast, type ToastItem } from './toast-core';

describe('createToast', () => {
	it('carries the id, kind and message', () => {
		const toast = createToast(1, 'success', 'Saved invoice-2026-06.pdf');
		expect(toast).toMatchObject({ id: 1, kind: 'success', message: 'Saved invoice-2026-06.pdf' });
	});

	it('gives an error a longer life than an acknowledgement', () => {
		expect(createToast(1, 'success', 'ok').timeoutMs).toBe(4000);
		expect(createToast(2, 'info', 'note').timeoutMs).toBe(4000);
		expect(createToast(3, 'error', 'failed').timeoutMs).toBe(7000);
	});

	it('accepts an explicit lifetime, including a sticky 0', () => {
		expect(createToast(1, 'info', 'note', 1500).timeoutMs).toBe(1500);
		expect(createToast(2, 'error', 'failed', 0).timeoutMs).toBe(0);
	});
});

describe('addToast', () => {
	it('appends without mutating the input', () => {
		const list: ToastItem[] = [createToast(1, 'info', 'first')];
		const next = addToast(list, createToast(2, 'info', 'second'));
		expect(list).toHaveLength(1);
		expect(next.map((t) => t.id)).toEqual([1, 2]);
	});

	it('drops the oldest once the cap is reached, keeping the newest', () => {
		let list: ToastItem[] = [];
		for (let id = 1; id <= MAX_TOASTS + 2; id += 1) {
			list = addToast(list, createToast(id, 'info', `toast ${id}`));
		}
		expect(list).toHaveLength(MAX_TOASTS);
		expect(list.map((t) => t.id)).toEqual([3, 4, 5, 6]);
	});

	it('honours an explicit cap', () => {
		let list: ToastItem[] = [];
		list = addToast(list, createToast(1, 'info', 'a'), 1);
		list = addToast(list, createToast(2, 'info', 'b'), 1);
		expect(list.map((t) => t.id)).toEqual([2]);
	});
});

describe('removeToast', () => {
	it('removes exactly the targeted toast', () => {
		const list = [createToast(1, 'info', 'a'), createToast(2, 'error', 'b')];
		expect(removeToast(list, 1).map((t) => t.id)).toEqual([2]);
	});

	it('is a no-op for an unknown id and never mutates', () => {
		const list = [createToast(1, 'info', 'a')];
		const next = removeToast(list, 99);
		expect(next.map((t) => t.id)).toEqual([1]);
		expect(next).not.toBe(list);
	});
});
