package xyz.goga221.koi.data;

import xyz.goga221.koi.fishing.FishCatch;
import xyz.goga221.koi.fishing.FishRarity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * DAO for the {@code catches} and {@code player_stats} tables: inserts a catch row and
 * recomputes that player's aggregate stats.
 */
public class CatchRepository {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public CatchRepository(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public void record(FishCatch catchRecord) {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            insertCatch(connection, catchRecord);
            upsertStats(connection, catchRecord);
            connection.commit();
        } catch (SQLException e) {
            logger.warning("Failed to persist catch for " + catchRecord.getPlayerId() + ": " + e.getMessage());
        }
    }

    public Set<String> findDiscoveredFishIds(UUID playerId) {
        Set<String> ids = new HashSet<>();
        String sql = "SELECT DISTINCT fish_id FROM catches WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("fish_id"));
                }
            }
        } catch (SQLException e) {
            logger.warning("Failed to load discovered fish for " + playerId + ": " + e.getMessage());
        }
        return ids;
    }

    public Optional<PlayerFishStats> findStats(UUID playerId) {
        String sql = "SELECT total_catches, best_rarity FROM player_stats WHERE player_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerFishStats stats = new PlayerFishStats(playerId);
                stats.setTotalCatches(rs.getInt("total_catches"));
                stats.setBestRarity(FishRarity.valueOf(rs.getString("best_rarity")));
                return Optional.of(stats);
            }
        } catch (SQLException e) {
            logger.warning("Failed to load stats for " + playerId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void insertCatch(Connection connection, FishCatch catchRecord) throws SQLException {
        String sql = "INSERT INTO catches (player_uuid, fish_id, rarity, rod_tier, bait_id, caught_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, catchRecord.getPlayerId().toString());
            statement.setString(2, catchRecord.getFishId());
            statement.setString(3, catchRecord.getRarity().name());
            statement.setString(4, catchRecord.getRodTier().name());
            statement.setString(5, catchRecord.getBaitId());
            statement.setLong(6, catchRecord.getCaughtAt());
            statement.executeUpdate();
        }
    }

    private void upsertStats(Connection connection, FishCatch catchRecord) throws SQLException {
        String playerUuid = catchRecord.getPlayerId().toString();
        int totalCatches = 1;
        FishRarity bestRarity = catchRecord.getRarity();

        String selectSql = "SELECT total_catches, best_rarity FROM player_stats WHERE player_uuid = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setString(1, playerUuid);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    totalCatches = rs.getInt("total_catches") + 1;
                    FishRarity existingBest = FishRarity.valueOf(rs.getString("best_rarity"));
                    if (existingBest.ordinal() > bestRarity.ordinal()) {
                        bestRarity = existingBest;
                    }
                }
            }
        }

        String upsertSql = """
                INSERT INTO player_stats (player_uuid, total_catches, best_rarity, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    total_catches = excluded.total_catches,
                    best_rarity = excluded.best_rarity,
                    updated_at = excluded.updated_at
                """;
        try (PreparedStatement upsert = connection.prepareStatement(upsertSql)) {
            upsert.setString(1, playerUuid);
            upsert.setInt(2, totalCatches);
            upsert.setString(3, bestRarity.name());
            upsert.setLong(4, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }
}
