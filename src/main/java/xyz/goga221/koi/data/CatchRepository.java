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

    // RodTier is now an admin-authored catalog entry (see item.RodTierPool), not a guaranteed-
    // baseline enum - a catch can legitimately have no rod tier (e.g. no tiers configured yet, or
    // a sea-creature kill with none recorded). rod_tier stays NOT NULL in the schema, so this
    // sentinel stands in for null rather than a live ALTER TABLE migration on production data.
    private static final String NO_ROD_TIER_ID = "none";

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

    /**
     * Bumps {@code sea_creatures_caught} for landing a sea-creature encounter's reel-in stage
     * (see {@code FishManager#resolveSession}) - lighter than {@link #record}, since reeling one
     * in doesn't guarantee the kill that follows, so it's tracked separately rather than as a
     * full {@code catches} row.
     */
    public void recordSeaCreatureCaught(UUID playerId, FishRarity rarity) {
        String sql = """
                INSERT INTO player_stats (player_uuid, total_catches, best_rarity, sea_creatures_caught, updated_at)
                VALUES (?, 0, ?, 1, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    sea_creatures_caught = sea_creatures_caught + 1,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, rarity.name());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to record sea creature catch for " + playerId + ": " + e.getMessage());
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
        String sql = "SELECT total_catches, best_rarity, sea_creatures_caught, sea_creatures_killed "
                + "FROM player_stats WHERE player_uuid = ?";
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
                stats.setSeaCreaturesCaught(rs.getInt("sea_creatures_caught"));
                stats.setSeaCreaturesKilled(rs.getInt("sea_creatures_killed"));
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
            statement.setString(4, catchRecord.getRodTier() != null ? catchRecord.getRodTier().getId() : NO_ROD_TIER_ID);
            statement.setString(5, catchRecord.getBaitId());
            statement.setLong(6, catchRecord.getCaughtAt());
            statement.executeUpdate();
        }
    }

    private void upsertStats(Connection connection, FishCatch catchRecord) throws SQLException {
        String playerUuid = catchRecord.getPlayerId().toString();
        int totalCatches = 1;
        FishRarity bestRarity = catchRecord.getRarity();
        int seaCreaturesKilled = catchRecord.isSeaCreature() ? 1 : 0;

        String selectSql = "SELECT total_catches, best_rarity, sea_creatures_killed FROM player_stats WHERE player_uuid = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setString(1, playerUuid);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    totalCatches = rs.getInt("total_catches") + 1;
                    FishRarity existingBest = FishRarity.valueOf(rs.getString("best_rarity"));
                    if (existingBest.ordinal() > bestRarity.ordinal()) {
                        bestRarity = existingBest;
                    }
                    seaCreaturesKilled = rs.getInt("sea_creatures_killed") + (catchRecord.isSeaCreature() ? 1 : 0);
                }
            }
        }

        String upsertSql = """
                INSERT INTO player_stats (player_uuid, total_catches, best_rarity, sea_creatures_killed, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    total_catches = excluded.total_catches,
                    best_rarity = excluded.best_rarity,
                    sea_creatures_killed = excluded.sea_creatures_killed,
                    updated_at = excluded.updated_at
                """;
        try (PreparedStatement upsert = connection.prepareStatement(upsertSql)) {
            upsert.setString(1, playerUuid);
            upsert.setInt(2, totalCatches);
            upsert.setString(3, bestRarity.name());
            upsert.setInt(4, seaCreaturesKilled);
            upsert.setLong(5, System.currentTimeMillis());
            upsert.executeUpdate();
        }
    }
}
