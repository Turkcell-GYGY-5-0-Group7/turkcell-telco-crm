import { describe, expect, it } from 'vitest';
import type { CommunicationPreference } from '$lib/api/client';
import { channelLabel, mergeChannelPreferences } from './channels';

function pref(channel: string, optedIn: boolean): CommunicationPreference {
	return { id: channel, userId: 'c', channel, optedIn, updatedAt: '2026-01-01T00:00:00Z' };
}

describe('channelLabel', () => {
	it('labels known channels and falls back to the code', () => {
		expect(channelLabel('EMAIL')).toBe('Email');
		expect(channelLabel('PUSH')).toBe('Push notifications');
		expect(channelLabel('WHATSAPP')).toBe('WHATSAPP');
	});
});

describe('mergeChannelPreferences', () => {
	it('defaults every known channel to opted-in when nothing is stored', () => {
		const merged = mergeChannelPreferences([]);
		expect(merged).toEqual([
			{ channel: 'EMAIL', optedIn: true, explicit: false },
			{ channel: 'SMS', optedIn: true, explicit: false },
			{ channel: 'PUSH', optedIn: true, explicit: false }
		]);
	});

	it('applies a stored opt-out over the default', () => {
		const merged = mergeChannelPreferences([pref('SMS', false)]);
		expect(merged.find((m) => m.channel === 'SMS')).toEqual({
			channel: 'SMS',
			optedIn: false,
			explicit: true
		});
	});

	it('is case-insensitive on the stored channel', () => {
		const merged = mergeChannelPreferences([pref('email', false)]);
		expect(merged.find((m) => m.channel === 'EMAIL')?.optedIn).toBe(false);
	});

	it('appends a server-only channel not in the known list', () => {
		const merged = mergeChannelPreferences([pref('WHATSAPP', true)]);
		expect(merged.map((m) => m.channel)).toContain('WHATSAPP');
	});
});
