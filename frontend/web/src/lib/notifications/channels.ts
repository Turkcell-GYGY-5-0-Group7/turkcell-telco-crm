// Pure helpers for the notification-preferences panel (unit tested in Node).
//
// notification-service stores one CommunicationPreference per (user, channel) but
// only for channels the user has an explicit row for. The panel wants to show ALL
// known channels with a sensible default (opted-in) for those with no stored row,
// so this merges the known channel list with whatever the server returned.

import type { CommunicationPreference } from '$lib/api/client';

/** The channels the notification service can deliver on (upper-cased, as stored). */
export const KNOWN_CHANNELS = ['EMAIL', 'SMS', 'PUSH'] as const;

export interface ChannelPreference {
	channel: string;
	optedIn: boolean;
	/** True when the value came from a stored row, false when it is the default. */
	explicit: boolean;
}

const CHANNEL_LABELS: Record<string, string> = {
	EMAIL: 'Email',
	SMS: 'SMS',
	PUSH: 'Push notifications'
};

/** A human label for a channel code, falling back to the raw code. */
export function channelLabel(channel: string): string {
	return CHANNEL_LABELS[channel.toUpperCase()] ?? channel;
}

/**
 * Merge stored preferences over the known-channel list. Every known channel appears
 * exactly once; a channel with a stored row takes that value (`explicit: true`),
 * otherwise it defaults to opted-in (`explicit: false`). Any stored channel that is
 * NOT in the known list is appended, so a server-added channel still renders.
 */
export function mergeChannelPreferences(
	stored: readonly CommunicationPreference[],
	known: readonly string[] = KNOWN_CHANNELS
): ChannelPreference[] {
	const byChannel = new Map<string, CommunicationPreference>();
	for (const pref of stored) {
		byChannel.set(pref.channel.toUpperCase(), pref);
	}

	const merged: ChannelPreference[] = known.map((channel) => {
		const row = byChannel.get(channel.toUpperCase());
		return row
			? { channel, optedIn: row.optedIn, explicit: true }
			: { channel, optedIn: true, explicit: false };
	});

	const knownUpper = new Set(known.map((c) => c.toUpperCase()));
	for (const pref of stored) {
		if (!knownUpper.has(pref.channel.toUpperCase())) {
			merged.push({ channel: pref.channel, optedIn: pref.optedIn, explicit: true });
		}
	}

	return merged;
}
