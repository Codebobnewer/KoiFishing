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
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Owns the SQLite connection pool backing catch history / leaderboard storage. Startup and
 * shutdown are both bounded, so a stalled/unreachable database (a locked file, a hung disk)
 * can't hang the whole server's boot or {@code /stop} sequence indefinitely.
 */
public class DatabaseManager {

    private static final long CLOSE_TIMEOUT_MILLIS = 5000L;

    private final Logger logger;
    private final HikariDataSource dataSource;
    @Getter
    private final CatchRepository catchRepository;

    public DatabaseManager(KoiPlugin plugin) {
        this.logger = plugin.getLogger();
        File dbFile = new File(plugin.getDataFolder(), plugin.getConfigManager().getDatabaseFileName());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite is single-writer
        // A local SQLite file should connect near-instantly; a stall means something's actually
        // wrong (a locked file, a hung disk). Fail fast instead of hanging server startup for
        // Hikari's 30s default.
        config.setConnectionTimeout(8000L);
        config.setInitializationFailTimeout(8000L);

        this.dataSource = new HikariDataSource(config);
        migrate();
        this.catchRepository = new CatchRepository(this, logger);
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

    /**
     * Closes the connection pool with a hard time budget. HikariCP's own close() waits for any
     * checked-out connection to be returned before it can finish - if a query is genuinely
     * stuck, that wait can be unbounded, which would hang the whole server's shutdown since
     * Paper/Folia waits for every plugin's onDisable to return before it can exit. Closing on a
     * daemon thread and giving up after a few seconds means a stalled database can, at worst,
     * leak that one connection rather than block the server from stopping.
     */
    public void close() {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }

        Thread closer = new Thread(dataSource::close, "Koi-DatabaseManager-close");
        closer.setDaemon(true);
        closer.start();

        try {
            closer.join(TimeUnit.MILLISECONDS.toMillis(CLOSE_TIMEOUT_MILLIS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (closer.isAlive()) {
            logger.warning("Koi's database connection pool didn't close within "
                    + CLOSE_TIMEOUT_MILLIS + "ms (looks stalled) - continuing shutdown anyway.");
        }
    }
}
