package app.deckplanner.collection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Atomically persists the most recent complete Arena-owned-card snapshot. */
public final class ArenaCollectionRepository implements AutoCloseable {
    private final Connection connection;

    public ArenaCollectionRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            initializeSchema();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize Arena collection repository", error);
        }
    }

    public synchronized void replaceComplete(ArenaCollectionSnapshot snapshot) {
        String snapshotId = UUID.randomUUID().toString();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement metadata = connection.prepareStatement("""
                    INSERT INTO arena_collection_snapshot
                        (snapshot_id, observed_at, source_sequence, source_kind)
                    VALUES (?, ?, ?, ?)
                    """)) {
                metadata.setString(1, snapshotId);
                metadata.setObject(2, snapshot.observedAt());
                metadata.setLong(3, snapshot.sourceSequence());
                metadata.setString(4, snapshot.source().name());
                metadata.executeUpdate();
            }
            try (PreparedStatement card = connection.prepareStatement("""
                    INSERT INTO arena_collection_card (snapshot_id, arena_id, copies)
                    VALUES (?, ?, ?)
                    """)) {
                for (Map.Entry<Long, Integer> entry : snapshot.ownedCopies().entrySet()) {
                    card.setString(1, snapshotId);
                    card.setLong(2, entry.getKey());
                    card.setInt(3, entry.getValue());
                    card.addBatch();
                }
                card.executeBatch();
            }
            try (PreparedStatement current = connection.prepareStatement("""
                    MERGE INTO arena_collection_current (singleton_key, snapshot_id)
                    KEY (singleton_key) VALUES (1, ?)
                    """)) {
                current.setString(1, snapshotId);
                current.executeUpdate();
            }
            connection.commit();
        } catch (SQLException error) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not replace Arena collection snapshot", error);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public synchronized CollectionQuantity quantity(long arenaId) {
        if (arenaId <= 0) throw new IllegalArgumentException("arenaId <= 0");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.copies
                FROM arena_collection_current current_snapshot
                LEFT JOIN arena_collection_card c
                    ON c.snapshot_id = current_snapshot.snapshot_id AND c.arena_id = ?
                WHERE current_snapshot.singleton_key = 1
                """)) {
            statement.setLong(1, arenaId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new CollectionQuantity(CollectionQuantity.UNKNOWN);
                int copies = result.getInt(1);
                return new CollectionQuantity(result.wasNull() ? 0 : copies);
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read collection quantity", error);
        }
    }

    /** Returns unknown when the latest complete observation is older than the requested age. */
    public synchronized CollectionQuantity quantity(long arenaId, Duration maximumAge) {
        if (maximumAge == null || maximumAge.isNegative()) {
            throw new IllegalArgumentException("maximumAge is invalid");
        }
        Optional<ArenaCollectionSnapshot> snapshot = current();
        if (snapshot.isEmpty()
                || snapshot.get().observedAt().isBefore(Instant.now().minus(maximumAge))) {
            return new CollectionQuantity(CollectionQuantity.UNKNOWN);
        }
        return quantity(arenaId);
    }

    public synchronized Optional<ArenaCollectionSnapshot> current() {
        try (PreparedStatement metadata = connection.prepareStatement("""
                SELECT s.snapshot_id, s.observed_at, s.source_sequence, s.source_kind
                FROM arena_collection_current c
                JOIN arena_collection_snapshot s ON s.snapshot_id = c.snapshot_id
                WHERE c.singleton_key = 1
                """)) {
            try (ResultSet result = metadata.executeQuery()) {
                if (!result.next()) return Optional.empty();
                String id = result.getString("snapshot_id");
                Map<Long, Integer> cards = new LinkedHashMap<>();
                try (PreparedStatement entries = connection.prepareStatement("""
                        SELECT arena_id, copies FROM arena_collection_card
                        WHERE snapshot_id = ? ORDER BY arena_id
                        """)) {
                    entries.setString(1, id);
                    try (ResultSet rows = entries.executeQuery()) {
                        while (rows.next()) cards.put(rows.getLong(1), rows.getInt(2));
                    }
                }
                return Optional.of(new ArenaCollectionSnapshot(cards,
                        result.getTimestamp("observed_at").toInstant(),
                        result.getLong("source_sequence"),
                        ArenaCollectionSnapshot.Source.valueOf(result.getString("source_kind"))));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read current collection snapshot", error);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arena_collection_snapshot (
                        snapshot_id VARCHAR(36) PRIMARY KEY,
                        observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        source_sequence BIGINT NOT NULL,
                        source_kind VARCHAR(64) NOT NULL)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arena_collection_card (
                        snapshot_id VARCHAR(36) NOT NULL,
                        arena_id BIGINT NOT NULL,
                        copies INTEGER NOT NULL,
                        PRIMARY KEY (snapshot_id, arena_id))
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arena_collection_current (
                        singleton_key INTEGER PRIMARY KEY CHECK (singleton_key = 1),
                        snapshot_id VARCHAR(36) NOT NULL)
                    """);
        }
    }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) { throw new IllegalStateException("Could not close collection repository", error); }
    }
}
