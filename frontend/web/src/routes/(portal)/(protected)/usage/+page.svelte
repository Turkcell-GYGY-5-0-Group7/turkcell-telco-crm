<script lang="ts">
	// Usage view: per-subscription quota and a cursor-paginated CDR history from the
	// gateway (usage-service). The subscription picker is fed by GET /bff/v1/account
	// (so an unlinked first-run user gets the onboarding notice, exactly like the
	// account and invoices pages); the quota and history come from the direct
	// gateway thin-slice reads the client exposes. usage-service scopes both to the
	// caller's own customerId, so no id is trusted from the browser.
	import { onMount } from 'svelte';
	import {
		ApiError,
		api,
		type AccountOverview,
		type Quota,
		type UsageHistoryItem
	} from '$lib/api/client';
	import { renewSession } from '$lib/auth/oidc';
	import { loadLinkedResource } from '$lib/onboarding/link-state';
	import NotOnboardedNotice from '$lib/onboarding/NotOnboardedNotice.svelte';
	import Alert from '$lib/ui/Alert.svelte';
	import Badge from '$lib/ui/Badge.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Card from '$lib/ui/Card.svelte';
	import EmptyState from '$lib/ui/EmptyState.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Skeleton from '$lib/ui/Skeleton.svelte';
	import Sparkline from '$lib/ui/Sparkline.svelte';
	import StatCard from '$lib/ui/StatCard.svelte';
	import Table from '$lib/ui/Table.svelte';
	import { gaugeTone, type BadgeTone } from '$lib/ui/status';
	import { dailyTotals, defaultRange, formatQuantity } from '$lib/usage/history';

	const HISTORY_PAGE = 25;
	const RANGE_OPTIONS = [
		{ value: '7', label: 'Last 7 days' },
		{ value: '30', label: 'Last 30 days' },
		{ value: '90', label: 'Last 90 days' }
	];

	let account = $state<AccountOverview | null>(null);
	let accountLoading = $state(true);
	let accountError = $state('');
	let notOnboarded = $state(false);

	let selectedSub = $state('');
	let rangeDays = $state('30');

	let quota = $state<Quota | null>(null);
	let items = $state<UsageHistoryItem[]>([]);
	let nextCursor = $state<string | null>(null);
	let hasNext = $state(false);
	let usageLoading = $state(false);
	let usageError = $state('');

	const subOptions = $derived(
		(account?.subscriptions ?? []).map((entry) => ({
			value: entry.subscription.subscriptionId,
			label: `${entry.subscription.msisdn} (${entry.subscription.tariffCode})`
		}))
	);

	// Daily DATA totals feed the trend line; mixing usage types in one sum would be
	// meaningless, so the sparkline reads a single type.
	const dataTrend = $derived(
		dailyTotals(items.filter((item) => item.type === 'DATA')).map((day) => day.total)
	);

	const columns = [
		{ key: 'recordedAt', label: 'When' },
		{ key: 'type', label: 'Type' },
		{ key: 'quantity', label: 'Quantity', align: 'right' as const },
		{ key: 'overage', label: 'Overage' }
	];

	onMount(() => {
		void loadAccount();
	});

	async function loadAccount() {
		accountLoading = true;
		accountError = '';
		notOnboarded = false;
		const result = await loadLinkedResource(() => api.getAccount(), { renewSession });
		if (result.state === 'loaded') {
			account = result.data;
			const first = result.data.subscriptions[0]?.subscription.subscriptionId ?? '';
			selectedSub = first;
			if (first) void loadUsage();
		} else if (result.state === 'unlinked') {
			notOnboarded = true;
		} else {
			accountError =
				result.error instanceof ApiError
					? `Could not load your subscriptions. (HTTP ${result.error.status})`
					: 'Could not load your subscriptions.';
		}
		accountLoading = false;
	}

	async function loadUsage() {
		if (!selectedSub) return;
		usageLoading = true;
		usageError = '';
		quota = null;
		items = [];
		nextCursor = null;
		hasNext = false;
		const { from, to } = defaultRange(Number(rangeDays));
		try {
			const [quotaResult, historyResult] = await Promise.all([
				api.getQuota(selectedSub).catch((err) => {
					// A subscription with no provisioned quota (suspended/terminated) is not
					// an error for the page; only a real failure is.
					if (err instanceof ApiError && (err.status === 404 || err.status === 403)) return null;
					throw err;
				}),
				api.getUsageHistory(selectedSub, from, to, undefined, HISTORY_PAGE)
			]);
			quota = quotaResult;
			items = historyResult.content;
			nextCursor = historyResult.nextCursor;
			hasNext = historyResult.hasNext;
		} catch (err) {
			usageError =
				err instanceof ApiError
					? `Could not load usage. (HTTP ${err.status})`
					: 'Could not load usage.';
		} finally {
			usageLoading = false;
		}
	}

	async function loadMore() {
		if (!hasNext || !nextCursor || !selectedSub) return;
		const { from, to } = defaultRange(Number(rangeDays));
		usageLoading = true;
		try {
			const page = await api.getUsageHistory(selectedSub, from, to, nextCursor, HISTORY_PAGE);
			items = [...items, ...page.content];
			nextCursor = page.nextCursor;
			hasNext = page.hasNext;
		} catch (err) {
			usageError =
				err instanceof ApiError
					? `Could not load more. (HTTP ${err.status})`
					: 'Could not load more.';
		} finally {
			usageLoading = false;
		}
	}

	function onControlsChange() {
		void loadUsage();
	}

	function usageTone(used: number, total: number): BadgeTone {
		if (total <= 0) return 'neutral';
		const tone = gaugeTone((used / total) * 100);
		return tone === 'ok' ? 'info' : tone;
	}

	function pct(used: number, total: number): string {
		if (total <= 0) return '0%';
		return `${Math.round((used / total) * 100)}% used`;
	}

	function fmtWhen(iso: string): string {
		return new Date(iso).toLocaleString();
	}
</script>

<section class="page">
	<PageHeader title="Usage" subtitle="Your allowance and recent activity, per line." />

	{#if accountLoading}
		<Card><Skeleton variant="text" width="40%" /></Card>
	{:else if accountError}
		<Alert tone="danger">
			{#snippet children()}<p>{accountError}</p>{/snippet}
			{#snippet actions()}
				<Button variant="secondary" size="sm" onclick={loadAccount}>Retry</Button>
			{/snippet}
		</Alert>
	{:else if notOnboarded}
		<NotOnboardedNotice
			message="You have not completed onboarding yet, so there is no line to report usage for. Your data, minutes, and SMS activity will appear here once your subscription is activated."
		/>
	{:else if subOptions.length === 0}
		<EmptyState
			title="No lines yet"
			message="Usage will appear here once you have an active subscription."
		>
			{#snippet action()}<Button href="/onboarding">Start onboarding</Button>{/snippet}
		</EmptyState>
	{:else}
		<Card>
			<div class="controls">
				<Select
					id="usage-sub"
					label="Subscription"
					bind:value={selectedSub}
					options={subOptions}
					onchange={onControlsChange}
				/>
				<Select
					id="usage-range"
					label="Period"
					bind:value={rangeDays}
					options={RANGE_OPTIONS}
					onchange={onControlsChange}
				/>
				<div class="apply">
					<Button variant="secondary" size="sm" onclick={onControlsChange} loading={usageLoading}>
						Refresh
					</Button>
				</div>
			</div>
		</Card>

		{#if usageError}
			<Alert tone="danger">
				{#snippet children()}<p>{usageError}</p>{/snippet}
				{#snippet actions()}
					<Button variant="secondary" size="sm" onclick={loadUsage}>Retry</Button>
				{/snippet}
			</Alert>
		{/if}

		{#if quota}
			<div class="stats">
				<StatCard
					label="Data remaining"
					value={`${quota.mbRemaining.toLocaleString()} / ${quota.mbTotal.toLocaleString()} MB`}
					hint={pct(quota.mbTotal - quota.mbRemaining, quota.mbTotal)}
					tone={usageTone(quota.mbTotal - quota.mbRemaining, quota.mbTotal)}
				/>
				<StatCard
					label="Minutes remaining"
					value={`${quota.minutesRemaining.toLocaleString()} / ${quota.minutesTotal.toLocaleString()}`}
					hint={pct(quota.minutesTotal - quota.minutesRemaining, quota.minutesTotal)}
					tone={usageTone(quota.minutesTotal - quota.minutesRemaining, quota.minutesTotal)}
				/>
				<StatCard
					label="SMS remaining"
					value={`${quota.smsRemaining.toLocaleString()} / ${quota.smsTotal.toLocaleString()}`}
					hint={pct(quota.smsTotal - quota.smsRemaining, quota.smsTotal)}
					tone={usageTone(quota.smsTotal - quota.smsRemaining, quota.smsTotal)}
				/>
			</div>
		{/if}

		{#if dataTrend.length > 1}
			<Card>
				<div class="trend">
					<h2>Daily data trend</h2>
					<Sparkline values={dataTrend} height={56} ariaLabel="Daily data usage trend" />
				</div>
			</Card>
		{/if}

		<Card padding="none">
			<Table
				{columns}
				rows={items}
				rowKey={(item) => item.id}
				loading={usageLoading && items.length === 0}
			>
				{#snippet cell(item, key)}
					{#if key === 'recordedAt'}
						<span class="when">{fmtWhen(item.recordedAt)}</span>
					{:else if key === 'type'}
						<Badge tone="neutral">{#snippet children()}{item.type}{/snippet}</Badge>
					{:else if key === 'quantity'}
						{formatQuantity(item)}
					{:else if key === 'overage'}
						{#if item.overage}
							<Badge tone="warning">{#snippet children()}Overage{/snippet}</Badge>
						{:else}
							<span class="muted">-</span>
						{/if}
					{/if}
				{/snippet}
				{#snippet empty()}
					<EmptyState title="No activity" message="No usage records in the selected period." />
				{/snippet}
			</Table>
		</Card>

		{#if hasNext}
			<div class="more">
				<Button variant="secondary" size="sm" onclick={loadMore} loading={usageLoading}>
					Load more
				</Button>
			</div>
		{/if}
	{/if}
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-5);
		max-width: 60rem;
	}

	.controls {
		display: grid;
		grid-template-columns: 2fr 1fr auto;
		align-items: end;
		gap: var(--space-4);
	}

	.apply {
		padding-bottom: 0.15rem;
	}

	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
		gap: var(--space-4);
	}

	.trend {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	h2 {
		font-size: var(--text-base-size);
		font-weight: 700;
	}

	.when {
		font-size: var(--text-sm-size);
		color: var(--color-text-secondary);
		white-space: nowrap;
	}

	.muted {
		color: var(--color-text-muted);
	}

	.more {
		display: flex;
		justify-content: center;
	}

	@media (max-width: 40rem) {
		.controls {
			grid-template-columns: 1fr;
		}
	}
</style>
