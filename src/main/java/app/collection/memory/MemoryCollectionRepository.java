package app.collection.memory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Map;
import java.util.LinkedHashMap;

/** Module-owned atomic ID-to-copies persistence with no dependency on log collection storage. */
final class MemoryCollectionRepository implements AutoCloseable {
    static final int UNKNOWN = -1;
    private final Connection connection;

    MemoryCollectionRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS memory_collection_owned (
                            arena_id BIGINT PRIMARY KEY,
                            copies INTEGER NOT NULL CHECK (copies >= 0))
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS memory_collection_verified_card (
                            arena_id BIGINT PRIMARY KEY,
                            copies INTEGER NOT NULL CHECK (copies BETWEEN 1 AND 400))
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS memory_collection_state (
                            singleton_key INTEGER PRIMARY KEY CHECK (singleton_key = 1),
                            complete BOOLEAN NOT NULL)
                        """);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize memory collection ownership", error);
        }
    }

    synchronized int getCopiesOwned(long arenaId) {
        if (arenaId <= 0) throw new IllegalArgumentException("arenaId <= 0");
        try (PreparedStatement complete = connection.prepareStatement(
                "SELECT complete FROM memory_collection_state WHERE singleton_key = 1")) {
            try (ResultSet state = complete.executeQuery()) {
                if (!state.next() || !state.getBoolean(1)) return UNKNOWN;
            }
            try (PreparedStatement owned = connection.prepareStatement(
                    "SELECT copies FROM memory_collection_owned WHERE arena_id = ?")) {
                owned.setLong(1, arenaId);
                try (ResultSet row = owned.executeQuery()) {
                    return row.next() ? row.getInt(1) : 0;
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read memory collection ownership", error);
        }
    }

    synchronized void replaceComplete(Map<Long, Integer> copies) {
        Map<Long, Integer> snapshot = Map.copyOf(copies == null ? Map.of() : copies);
        validate(snapshot);
        try {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM memory_collection_owned");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO memory_collection_owned (arena_id, copies) VALUES (?, ?)")) {
                for (Map.Entry<Long, Integer> entry : snapshot.entrySet()) {
                    insert.setLong(1, entry.getKey());
                    insert.setInt(2, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (PreparedStatement state = connection.prepareStatement("""
                    MERGE INTO memory_collection_state (singleton_key, complete)
                    KEY (singleton_key) VALUES (1, TRUE)
                    """)) {
                state.executeUpdate();
            }
            connection.commit();
        } catch (SQLException error) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not replace memory collection ownership", error);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    synchronized void replaceVerifiedCards(Map<Long, Integer> cards) {
        Map<Long, Integer> snapshot = Map.copyOf(cards == null ? Map.of() : cards);
        if (snapshot.size() < 2) throw new IllegalArgumentException("At least two verified cards are required");
        snapshot.forEach((id, copies) -> {
            if (id == null || id <= 0 || copies == null || copies < 1 || copies > 400) {
                throw new IllegalArgumentException("Invalid verified card quantity");
            }
        });
        try {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM memory_collection_verified_card");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO memory_collection_verified_card (arena_id, copies) VALUES (?, ?)")) {
                for (var entry : snapshot.entrySet()) {
                    insert.setLong(1, entry.getKey());
                    insert.setInt(2, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not replace verified cards", error);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    synchronized Map<Long, Integer> verifiedCardsPreferred() {
        Map<Long, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT arena_id, copies FROM memory_collection_verified_card
                ORDER BY CASE WHEN copies = 4 THEN 0 ELSE 1 END, arena_id
                """)) {
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.put(rows.getLong(1), rows.getInt(2));
            }
            return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read verified cards", error);
        }
    }

    private static void validate(Map<Long, Integer> copies) {
        for (Map.Entry<Long, Integer> entry : copies.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0) {
                throw new IllegalArgumentException("Invalid Arena id: " + entry.getKey());
            }
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Invalid copies for " + entry.getKey());
            }
        }
    }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) { throw new IllegalStateException("Could not close memory collection ownership", error); }
    }
}
