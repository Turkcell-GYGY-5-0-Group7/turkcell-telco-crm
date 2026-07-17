<script lang="ts">
	// Notifications view: a history of messages sent to the caller and the channel
	// opt-in preferences, on two tabs. Both are scoped to the caller's own customerId
	// (notification-service keys history and preferences by it, and the gateway
	// requires the path id to equal the caller's claim), so an unlinked user gets the
	// onboarding notice. Toggling a channel persists immediately via PUT.
	import { onMount } from 'svelte';
	import { ApiError, api, type NotificationRecord, type PageResult } from '$lib/api/client';
	import { currentCustomerId } from '$lib/auth/session';
	import {
		channelLabel,
		mergeChannelPreferences,
		type ChannelPreference
	} from '$lib/notifications/channels';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Pagination from '$lib/ui/Pagination.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Table from '$lib/ui/Table.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';
	import { notificationTone } from '$lib/ui/status';
	import { toasts } from '$lib/ui/toast.svelte';

	const PAGE_SIZE = 10;
	const TABS = [
		{ id: 'history', label: 'History' },
		{ id: 'preferences', label: 'Preferences' }
	];

	let customerId = $state<string | null>(null);
	let active = $state('history');

	let history = $state<PageResult<NotificationRecord> | null>(null);
	let historyPage = $state(0);
	let historyLoading = $state(true);
	let historyError = $state('');

	let prefs = $state<ChannelPreference[]>([]);
	let prefsLoading = $state(true);
	let prefsError = $state('');
	let savingChannel = $state<string | null>(null);

	const historyColumns = [
		{ key: 'createdAt', label: 'When' },
		{ key: 'templateCode', label: 'Message' },
		{ key: 'channel', label: 'Channel' },
		{ key: 'status', label: 'Status' }
	];

	onMount(() => {
		customerId = currentCustomerId();
		if (customerId) {
			void loadHistory(0);
			void loadPrefs();
		} else {
			historyLoading = false;
			prefsLoading = false;
		}
	});

	async function loadHistory(target: number) {
		if (!customerId) return;
		historyLoading = true;
		historyError = '';
		try {
			const data = await api.getNotificationHistory(customerId, target, PAGE_SIZE);
			history = data;
			historyPage = data.page;
		} catch (err) {
			historyError =
				err instanceof ApiError
					? `Could not load history. (HTTP ${err.status})`
					: 'Could not load history.';
		} finally {
			historyLoading = false;
		}
	}

	async function loadPrefs() {
		if (!customerId) return;
		prefsLoading = true;
		prefsError = '';
		try {
			const stored = await api.getNotificationPreferences(customerId);
			prefs = mergeChannelPreferences(stored);
		} catch (err) {
			prefsError =
				err instanceof ApiError
					? `Could not load preferences. (HTTP ${err.status})`
					: 'Could not load preferences.';
		} finally {
			prefsLoading = false;
		}
	}

	async function toggle(pref: ChannelPreference) {
		if (!customerId) return;
		savingChannel = pref.channel;
		const nextValue = !pref.optedIn;
		try {
			const saved = await api.updateNotificationPreference(customerId, pref.channel, nextValue);
			prefs = prefs.map((p) =>
				p.channel === pref.channel ? { ...p, optedIn: saved.optedIn, explicit: true } : p
			);
			toasts.success(`${channelLabel(pref.channel)} ${saved.optedIn ? 'enabled' : 'disabled'}.`);
		} catch (err) {
			toasts.error(
				err instanceof ApiError
					? `Could not save. (HTTP ${err.status})`
					: 'Could not save the preference.'
			);
		} finally {
			savingChannel = null;
		}
	}

	function fmtDateTime(iso: string): string {
		return new Date(iso).toLocaleString();
	}
</script>

<section class="page">
	<PageHeader
		title="Notifications"
		subtitle="Messages we have sent you, and how you hear from us."
	/>

	{#if customerId === null}
		<NotOnboardedNotice
			message="Once your account is active, the messages we send you and your channel preferences will appear here."
		/>
	{:else}
		<Tabs tabs={TABS} bind:active>
			{#snippet panel(id)}
				{#if id === 'history'}
					{#if historyLoading}
						<Card padding="none">
							<Table
								columns={historyColumns}
								rows={[] as NotificationRecord[]}
								rowKey={(n) => n.id}
								loading
							>
								{#snippet cell()}{/snippet}
							</Table>
						</Card>
					{:else if historyError}
						<Alert tone="danger">
							{#snippet children()}<p>{historyError}</p>{/snippet}
							{#snippet actions()}
								<Button variant="secondary" size="sm" onclick={() => loadHistory(historyPage)}
									>Retry</Button
								>
							{/snippet}
						</Alert>
					{:else if history}
						<Card padding="none">
							<Table columns={historyColumns} rows={history.content} rowKey={(n) => n.id}>
								{#snippet cell(record, key)}
									{#if key === 'createdAt'}
										<span class="when">{fmtDateTime(record.createdAt)}</span>
									{:else if key === 'templateCode'}
										{record.templateCode}
									{:else if key === 'channel'}
										<Badge tone="neutral">{#snippet children()}{record.channel}{/snippet}</Badge>
									{:else if key === 'status'}
										<Badge tone={notificationTone(record.status)}>
											{#snippet children()}{record.status}{/snippet}
										</Badge>
									{/if}
								{/snippet}
								{#snippet empty()}
									<EmptyState
										title="No messages yet"
										message="Notifications we send you - like invoice and usage alerts - will be listed here."
									/>
								{/snippet}
							</Table>
						</Card>
						{#if history.totalPages > 1}
							<div class="pager-wrap">
								<Pagination
									page={historyPage}
									totalPages={history.totalPages}
									disabled={historyLoading}
									onPrev={() => loadHistory(historyPage - 1)}
									onNext={() => loadHistory(historyPage + 1)}
								/>
							</div>
						{/if}
					{/if}
				{:else if id === 'preferences'}
					{#if prefsLoading}
						<Card><Skeleton variant="text" lines={3} /></Card>
					{:else if prefsError}
						<Alert tone="danger">
							{#snippet children()}<p>{prefsError}</p>{/snippet}
							{#snippet actions()}
								<Button variant="secondary" size="sm" onclick={loadPrefs}>Retry</Button>
							{/snippet}
						</Alert>
					{:else}
						<Card>
							<p class="hint">Choose how you want to hear from us. Changes save immediately.</p>
							<ul class="prefs">
								{#each prefs as pref (pref.channel)}
									<li>
										<div class="p-label">
											<span class="p-channel">{channelLabel(pref.channel)}</span>
											<span class="p-state">{pref.optedIn ? 'On' : 'Off'}</span>
										</div>
										<Button
											variant={pref.optedIn ? 'secondary' : 'primary'}
											size="sm"
											loading={savingChannel === pref.channel}
											onclick={() => toggle(pref)}
										>
											{pref.optedIn ? 'Turn off' : 'Turn on'}
										</Button>
									</li>
								{/each}
							</ul>
						</Card>
					{/if}
				{/if}
			{/snippet}
		</Tabs>
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 56rem;
	}

	.when {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
		white-space: nowrap;
	}

	.pager-wrap {
		margin-top: var(--space-4);
	}

	.hint {
		margin-bottom: var(--space-4);
		font-size: var(--text-sm-size);
		color: var(--color-text-muted);
	}

	.prefs {
		list-style: none;
		display: flex;
		flex-direction: column;
	}

	.prefs li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-4) 0;
		border-bottom: 1px solid var(--color-border);
	}

	.prefs li:last-child {
		border-bottom: 0;
	}

	.p-label {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}

	.p-channel {
		font-size: var(--text-sm-size);
		font-weight: 600;
	}

	.p-state {
		font-size: var(--text-xs-size);
		color: var(--color-text-muted);
	}
</style>
