import { describe, expect, it, vi } from 'vitest';
import { triggerBlobDownload, type DownloadAnchor, type DownloadPort } from './download';

/** A recording port so the browser-only download is verified without a DOM. */
function recordingPort() {
	const anchor: DownloadAnchor & { clicked: number } = {
		href: '',
		download: '',
		clicked: 0,
		click() {
			this.clicked += 1;
		}
	};
	const revoked: string[] = [];
	const port: DownloadPort = {
		createObjectURL: vi.fn(() => 'blob:fake-url'),
		revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
		createAnchor: () => anchor
	};
	return { port, anchor, revoked };
}

describe('triggerBlobDownload', () => {
	it('binds the object URL and filename to an anchor and clicks it once', () => {
		const { port, anchor } = recordingPort();
		const blob = new Blob(['pdf'], { type: 'application/pdf' });

		triggerBlobDownload(blob, 'invoice-2026-06.pdf', port);

		expect(port.createObjectURL).toHaveBeenCalledWith(blob);
		expect(anchor.href).toBe('blob:fake-url');
		expect(anchor.download).toBe('invoice-2026-06.pdf');
		expect(anchor.clicked).toBe(1);
	});

	it('revokes the object URL afterwards, even if the click throws', () => {
		const { port, revoked } = recordingPort();
		(port.createAnchor as () => DownloadAnchor) = () => ({
			href: '',
			download: '',
			click() {
				throw new Error('click blocked');
			}
		});

		expect(() => triggerBlobDownload(new Blob(['x']), 'f.pdf', port)).toThrow('click blocked');
		expect(revoked).toEqual(['blob:fake-url']);
	});
});
