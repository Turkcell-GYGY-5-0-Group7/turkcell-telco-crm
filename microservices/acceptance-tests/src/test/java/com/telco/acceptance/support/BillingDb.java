package com.telco.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only JDBC access to {@code billing_db.overage_records}, host-published Postgres port.
 *
 * <p><b>Why a direct database read (documented exception):</b> billing-service exposes no read API
 * for {@code overage_records} - it is an internal input to bill-run's {@code generateInvoice}, not
 * a public resource - and it is populated by an async {@code usage.aggregated.v1} consumer with no
 * synchronous signal back to the caller of the usage-aggregate endpoint. Same precedent as
 * {@link CampaignDb}: where no public surface exists, the suite touches the real infrastructure
 * directly and says why. Reads only; this class never mutates state.
 */
public final class BillingDb {

    private BillingDb() {
    }

    /**
     * Latest recorded data-overage amount (KB) for the given subscription, across any period;
     * empty while {@code UsageAggregatedBillingConsumer} has not processed the aggregation event
     * yet. Callers await this before triggering a bill-run for the same subscription - see
     * {@link AcceptanceConfig#BILLING_DB_JDBC_URL} javadoc for why the ordering matters.
     */
    public static Optional<Long> latestDataOverageKbForSubscription(UUID subscriptionId) {
        String sql = "SELECT data_overage_kb FROM overage_records WHERE subscription_id = ? "
                + "ORDER BY created_at DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                AcceptanceConfig.BILLING_DB_JDBC_URL,
                AcceptanceConfig.BILLING_DB_USER,
                AcceptanceConfig.BILLING_DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, subscriptionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getLong("data_overage_kb"))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "billing_db overage_records lookup failed for subscription " + subscriptionId, e);
        }
    }
}
