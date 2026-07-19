package com.telco.acceptance.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only JDBC access to {@code campaign_db.campaign_redemptions}, host-published Postgres port.
 *
 * <p><b>Why a direct database read (documented exception):</b> campaign-service exposes no
 * redemption read API and {@code ConfirmRedemptionCommandHandler} publishes no outbox event
 * (Feature 21.4 scope), so the RESERVED -&gt; CONFIRMED redemption lifecycle - the whole point of
 * the Sprint 21 saga integration - is otherwise unobservable from outside the service. Same
 * precedent as this suite's raw Kafka producer for AC-03 ({@link CdrEventProducer}): where no
 * public surface exists, the suite touches the real infrastructure directly and says why.
 * Reads only; this class never mutates state.
 */
public final class CampaignDb {

    private CampaignDb() {
    }

    /**
     * Latest redemption status recorded for the given order ({@code campaign_redemptions.order_id}
     * is written by {@code CreateRedemptionReservationCommandHandler} on {@code order.created.v1});
     * empty while the reservation consumer has not processed the order yet.
     */
    public static Optional<String> redemptionStatusForOrder(UUID orderId) {
        String sql = "SELECT status FROM campaign_redemptions WHERE order_id = ? "
                + "ORDER BY redeemed_at DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(
                AcceptanceConfig.CAMPAIGN_DB_JDBC_URL,
                AcceptanceConfig.CAMPAIGN_DB_USER,
                AcceptanceConfig.CAMPAIGN_DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getString("status"))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "campaign_db redemption lookup failed for order " + orderId, e);
        }
    }
}
