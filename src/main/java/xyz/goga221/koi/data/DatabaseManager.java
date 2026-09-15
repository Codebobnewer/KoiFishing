package xyz.goga221.koi.data;

import xyz.goga221.koi.KoiPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the SQLite connection pool backing catch history / leaderboard storage.
 */
public class DatabaseManager {

    private final HikariDataSource dataSource;
    @Getter
    private final CatchRepository catchRepository;

    public DatabaseManager(KoiPlugin plugin) {
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getDatabaseFileName());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite is single-writer

        this.dataSource = new HikariDataSource(config);
        migrate();
        this.catchRepository = new CatchRepository(this, plugin.getLogger());
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS catches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        fish_id TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        rod_tier TEXT NOT NULL,
                        bait_id TEXT,
                        caught_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_uuid TEXT PRIMARY KEY,
                        total_catches INTEGER NOT NULL,
                        best_rarity TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);

            // CREATE TABLE IF NOT EXISTS is a no-op against a db from before the "weight" fields
            // were dropped - an already-existing catches/player_stats table still has those old
            // NOT NULL columns, which silently fails every catch insert from then on. Drop them
            // if still present so the schema actually matches what the code writes.
            dropColumnIfExists(connection, statement, "catches", "weight");
            dropColumnIfExists(connection, statement, "player_stats", "heaviest_catch");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate Koi database", e);
        }
    }

    private void dropColumnIfExists(Connection connection, Statement statement, String table, String column) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) {
                statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
            }
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
