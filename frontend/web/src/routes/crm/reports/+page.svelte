<script lang="ts">
	// Reports is a planned surface: there are no analytics/reporting endpoints, so this
	// page describes the intended dashboards rather than inventing numbers.
	import ComingSoon from '$lib/ui/ComingSoon.svelte';
	import PageHeader from '$lib/ui/PageHeader.svelte';
</script>

<section class="page">
	<PageHeader title="Reports" subtitle="Operational and financial reporting." />
	<ComingSoon
		title="Reporting & analytics"
		message="Revenue, churn, activation-funnel, and support-SLA reports are planned. They need aggregation endpoints the platform does not expose yet; the per-service data behind them is available today through the other console sections."
		cta="Request a report"
	/>
</section>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: var(--space-6);
	}
</style>
