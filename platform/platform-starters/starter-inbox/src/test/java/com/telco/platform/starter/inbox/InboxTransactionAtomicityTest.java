package com.telco.platform.starter.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telco.platform.cqrs.Command;
import com.telco.platform.cqrs.CommandHandler;
import com.telco.platform.inbox.DefaultInboxService;
import com.telco.platform.inbox.IdempotentRequest;
import com.telco.platform.inbox.InboxBehavior;
import com.telco.platform.mediator.HandlerRegistry;
import com.telco.platform.mediator.InProcessMediator;
import com.telco.platform.mediator.behavior.TransactionBehavior;
import com.telco.platform.mediator.behavior.support.TransactionRunner;
import com.telco.platform.mediator.pipeline.PipelineBehavior;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the inbox dedup row and the handler's writes share one transaction (exactly-once-effect,
 * ADR-005). This is the regression guard for the tech-lead-ratified atomicity bug: {@code INBOX} now
 * runs INNER to {@code TRANSACTION}, so {@link JdbcInboxStore#markProcessed} joins the handler's open
 * transaction through the same {@link JdbcTemplate}/{@link DataSource}/{@link DataSourceTransactionManager}.
 *
 * <p>The whole production pipeline is wired with real components: the real {@link InProcessMediator}
 * applies the real {@link PipelineBehavior#order()} of {@link TransactionBehavior} and {@link InboxBehavior},
 * against a real PostgreSQL via Testcontainers so a rollback is a genuine database rollback. The only
 * stand-in is a one-line {@link TransactionRunner} mirroring the production {@code SpringTransactionRunner}
 * adapter (which lives in starter-mediator and is not a dependency here).
 */
class InboxTransactionAtomicityTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final String HANDLER = GuardedCommand.class.getName();

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    private InProcessMediator mediator;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Production inbox table (V901) plus a marker table standing in for the handler's business write.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS inbox_message (
                    message_id   VARCHAR(255) NOT NULL,
                    handler      VARCHAR(255) NOT NULL,
                    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                    PRIMARY KEY (message_id, handler)
                )""");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS handler_effect (
                    message_id VARCHAR(255) PRIMARY KEY
                )""");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetTablesAndWirePipeline() {
        jdbcTemplate.update("TRUNCATE inbox_message");
        jdbcTemplate.update("TRUNCATE handler_effect");

        // Same JdbcTemplate -> same DataSource that the transaction manager governs: the inbox INSERT
        // and the handler write enlist in the one transaction TransactionBehavior opens.
        JdbcInboxStore store = new JdbcInboxStore(jdbcTemplate, "inbox_message");
        InboxBehavior inboxBehavior = new InboxBehavior(new DefaultInboxService(store));

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        TransactionRunner runner = new TransactionRunner() {
            @Override
            public <R> R executeInTransaction(Supplier<R> action) {
                return transactionTemplate.execute(status -> action.get());
            }
        };
        TransactionBehavior transactionBehavior = new TransactionBehavior(runner);

        HandlerRegistry registry = new StaticRegistry(new GuardedCommandHandler(jdbcTemplate));
        List<PipelineBehavior> behaviors = List.of(inboxBehavior, transactionBehavior);
        mediator = new InProcessMediator(registry, behaviors);
    }

    @Test
    void handlerRollbackAlsoRollsBackTheInboxRowSoRedeliveryReRuns() {
        String messageId = UUID.randomUUID().toString();

        // First delivery: the handler throws AFTER InboxBehavior inserted the inbox row.
        assertThatThrownBy(() -> mediator.send(new GuardedCommand(messageId, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        // The inbox row was rolled back together with the (failed) handler write: nothing committed.
        assertThat(inboxRows(messageId)).isZero();
        assertThat(effectRows(messageId)).isZero();

        // Redelivery is therefore treated as first-seen and the handler runs again, this time
        // succeeding: inbox row and effect commit together.
        String result = mediator.send(new GuardedCommand(messageId, false));
        assertThat(result).isEqualTo("handled");
        assertThat(inboxRows(messageId)).isEqualTo(1);
        assertThat(effectRows(messageId)).isEqualTo(1);
    }

    @Test
    void firstDeliveryCommitsInboxAndEffectTogetherDuplicateIsSkipped() {
        String messageId = UUID.randomUUID().toString();

        String first = mediator.send(new GuardedCommand(messageId, false));
        assertThat(first).isEqualTo("handled");
        assertThat(inboxRows(messageId)).isEqualTo(1);
        assertThat(effectRows(messageId)).isEqualTo(1);

        // Duplicate: InboxBehavior short-circuits (firstSeen=false), the handler does NOT run again,
        // and no second effect row is written.
        String duplicate = mediator.send(new GuardedCommand(messageId, true));
        assertThat(duplicate).isNull();
        assertThat(inboxRows(messageId)).isEqualTo(1);
        assertThat(effectRows(messageId)).isEqualTo(1);
    }

    private int inboxRows(String messageId) {
        return rowCount("SELECT count(*) FROM inbox_message WHERE message_id = ? AND handler = ?",
                messageId, HANDLER);
    }

    private int effectRows(String messageId) {
        return rowCount("SELECT count(*) FROM handler_effect WHERE message_id = ?", messageId);
    }

    private int rowCount(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    /** Mutating command guarded by the inbox; {@code fail} forces a post-write rollback. */
    record GuardedCommand(String messageId, boolean fail) implements Command<String>, IdempotentRequest {
        @Override
        public String idempotencyKey() {
            return messageId;
        }
    }

    /** Writes its effect row inside the transaction, then optionally throws to trigger a rollback. */
    static final class GuardedCommandHandler implements CommandHandler<GuardedCommand, String> {
        private final JdbcTemplate jdbcTemplate;

        GuardedCommandHandler(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public String handle(GuardedCommand command) {
            jdbcTemplate.update("INSERT INTO handler_effect (message_id) VALUES (?)", command.messageId());
            if (command.fail()) {
                throw new IllegalStateException("boom");
            }
            return "handled";
        }
    }

    /** Minimal {@link HandlerRegistry} returning the single command handler under test. */
    static final class StaticRegistry implements HandlerRegistry {
        private final GuardedCommandHandler handler;

        StaticRegistry(GuardedCommandHandler handler) {
            this.handler = handler;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> CommandHandler<Command<R>, R> commandHandler(Class<?> commandType) {
            return (CommandHandler<Command<R>, R>) (CommandHandler<?, ?>) handler;
        }

        @Override
        public <R> com.telco.platform.cqrs.QueryHandler<com.telco.platform.cqrs.Query<R>, R>
                queryHandler(Class<?> queryType) {
            return null;
        }

        @Override
        public List<com.telco.platform.cqrs.EventHandler<com.telco.platform.cqrs.Event>>
                eventHandlers(Class<?> eventType) {
            return List.of();
        }
    }
}
