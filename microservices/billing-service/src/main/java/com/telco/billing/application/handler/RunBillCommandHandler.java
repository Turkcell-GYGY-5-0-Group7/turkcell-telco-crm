package com.telco.billing.application.handler;

import com.telco.billing.application.command.RunBillCommand;
import com.telco.billing.application.command.RunBillResult;
import com.telco.billing.infrastructure.entity.SubscriberBillingRecord;
import com.telco.billing.infrastructure.persistence.SubscriberBillingRecordRepository;
import com.telco.platform.common.context.CorrelationContext;
import com.telco.platform.common.context.CorrelationContextHolder;
import com.telco.platform.cqrs.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates the monthly bill-run (NFR-02: 100K active postpaid subscribers in under 30 minutes,
 * no duplicate invoices).
 *
 * <p>Fetches the active-subscriber read model, partitions it into bounded batches, and runs those
 * batches concurrently via {@link BillRunBatchProcessor}, each committing in its own transaction
 * (see {@link BillRunBatchProcessor} javadoc for why {@code REQUIRES_NEW} is safe here). This
 * handler itself does no persistence — it stays a thin orchestrator, matching the Domain
 * Orchestration mode (ADR-004): the mediator is the single entry point (no bypass), invoice writes
 * still flow through the repository/outbox exactly as before (ADR-009), and idempotency is
 * preserved both at the application layer (existence check per subscriber, in
 * {@link BillRunBatchProcessor}) and at the database layer (the {@code uidx_invoices_sub_period}
 * unique index).
 *
 * <p>Batch size and parallelism are externally tunable
 * ({@code telco.billing.bill-run.batch-size} / {@code telco.billing.bill-run.parallelism}) so the
 * throughput target can be met without a code change as subscriber volume grows; see
 * {@code docs/tasks/sprint-14-testing-and-hardening/14.3-bill-run-throughput-report.md} for the
 * measured tuning.
 */
@Component
public class RunBillCommandHandler implements CommandHandler<RunBillCommand, RunBillResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunBillCommandHandler.class);

    private final SubscriberBillingRecordRepository subscriberRepo;
    private final BillRunBatchProcessor batchProcessor;
    private final int batchSize;
    private final int parallelism;

    public RunBillCommandHandler(
            SubscriberBillingRecordRepository subscriberRepo,
            BillRunBatchProcessor batchProcessor,
            @Value("${telco.billing.bill-run.batch-size:500}") int batchSize,
            @Value("${telco.billing.bill-run.parallelism:8}") int parallelism) {
        this.subscriberRepo = subscriberRepo;
        this.batchProcessor = batchProcessor;
        this.batchSize = Math.max(1, batchSize);
        this.parallelism = Math.max(1, parallelism);
    }

    @Override
    public RunBillResult handle(RunBillCommand command) {
        List<SubscriberBillingRecord> activeSubscribers =
                subscriberRepo.findByStatus(SubscriberBillingRecord.ACTIVE);

        if (activeSubscribers.isEmpty()) {
            LOGGER.info("Bill-run: no active subscribers for period=[{},{})",
                    command.periodStart(), command.periodEnd());
            return new RunBillResult(0, 0);
        }

        List<List<SubscriberBillingRecord>> batches = partition(activeSubscribers, batchSize);
        int workers = Math.min(parallelism, batches.size());
        // The mediator's own TransactionBehavior wraps this entire handle() call in a transaction
        // (ADR-009's atomic outbox guarantee applies per command); capture the correlation context
        // bound to this thread so worker threads can carry the same traceId/correlationId onto the
        // batches they commit independently (ADR-012).
        CorrelationContext correlation = CorrelationContextHolder.get().orElse(null);

        LOGGER.info("Bill-run starting period=[{},{}) subscribers={} batches={} batchSize={} parallelism={}",
                command.periodStart(), command.periodEnd(), activeSubscribers.size(),
                batches.size(), batchSize, workers);

        ExecutorService executor = Executors.newFixedThreadPool(workers, billRunThreadFactory());
        try {
            List<Future<BillRunBatchProcessor.BatchOutcome>> futures = new ArrayList<>(batches.size());
            for (List<SubscriberBillingRecord> batch : batches) {
                futures.add(executor.submit(() -> runBatch(batch, command, correlation)));
            }

            int generated = 0;
            int skipped = 0;
            int failedBatches = 0;
            for (Future<BillRunBatchProcessor.BatchOutcome> future : futures) {
                try {
                    BillRunBatchProcessor.BatchOutcome outcome = future.get();
                    generated += outcome.generated();
                    skipped += outcome.skipped();
                } catch (ExecutionException e) {
                    // A whole batch can only fail here for an error outside the per-subscriber
                    // try/catch inside processBatch (e.g. a transaction-level failure); the other
                    // batches are unaffected since each commits independently.
                    failedBatches++;
                    LOGGER.error("Bill-run batch failed", e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Bill-run interrupted", e);
                }
            }

            LOGGER.info("Bill-run complete period=[{},{}) generated={} skipped={} failedBatches={}",
                    command.periodStart(), command.periodEnd(), generated, skipped, failedBatches);
            return new RunBillResult(generated, skipped);
        } finally {
            executor.shutdown();
            awaitTermination(executor);
        }
    }

    private BillRunBatchProcessor.BatchOutcome runBatch(
            List<SubscriberBillingRecord> batch, RunBillCommand command, CorrelationContext correlation) {
        if (correlation != null) {
            CorrelationContextHolder.set(correlation);
        }
        try {
            return batchProcessor.processBatch(batch, command);
        } finally {
            CorrelationContextHolder.clear();
        }
    }

    private static List<List<SubscriberBillingRecord>> partition(
            List<SubscriberBillingRecord> source, int size) {
        List<List<SubscriberBillingRecord>> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            result.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return result;
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(25, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static ThreadFactory billRunThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "bill-run-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
