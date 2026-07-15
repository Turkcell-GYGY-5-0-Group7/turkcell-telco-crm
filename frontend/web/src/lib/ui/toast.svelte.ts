// Reactive toast queue. A thin runes wrapper around the pure rules in toast-core:
// this owns only the reactive list and the auto-dismiss timers.

import { addToast, createToast, removeToast, type ToastItem, type ToastKind } from './toast-core';

class Toaster {
	items = $state<ToastItem[]>([]);

	#nextId = 1;

	/** Queue a toast. `timeoutMs: 0` keeps it up until it is dismissed. */
	show(kind: ToastKind, message: string, timeoutMs?: number) {
		const toast = createToast(this.#nextId++, kind, message, timeoutMs);
		this.items = addToast(this.items, toast);
		if (toast.timeoutMs > 0) {
			setTimeout(() => this.dismiss(toast.id), toast.timeoutMs);
		}
	}

	success(message: string) {
		this.show('success', message);
	}

	error(message: string) {
		this.show('error', message);
	}

	info(message: string) {
		this.show('info', message);
	}

	dismiss(id: number) {
		this.items = removeToast(this.items, id);
	}
}

export const toasts = new Toaster();
