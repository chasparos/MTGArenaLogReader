package app.enrichment;

import app.model.card.CardInfo;
import com.google.gson.Gson;

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
import java.util.List;
import java.util.Optional;

/** Persistent positive and negative cache for Arena card ID lookups.
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class CardCache implements AutoCloseable {
    /** Increment when the persisted CardInfo shape/enrichment guarantees change. */
    public static final int CURRENT_CACHE_VERSION = 3;

    private final Gson gson;
    private final Connection connection;

    public CardCache(Gson gson, Path databasePath) {
        this.gson = gson;
        try {
            Path absolute = databasePath.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) Files.createDirectories(parent);
            String url = "jdbc:h2:file:" + absolute.toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
            connection = DriverManager.getConnection(url, "sa", "");
            initializeSchema();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize H2 card cache", error);
        }
    }

    public synchronized Optional<CachedCard> find(long arenaId) {
        String sql = "SELECT found, card_json, updated_at, cache_version FROM arena_card_cache WHERE arena_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, arenaId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                boolean found = result.getBoolean("found");
                String json = result.getString("card_json");
                CardInfo card = found && json != null ? gson.fromJson(json, CardInfo.class) : null;
                Instant updatedAt = result.getTimestamp("updated_at").toInstant();
                int version = result.getInt("cache_version");
                return Optional.of(new CachedCard(found, Optional.ofNullable(card), updatedAt, version));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read card cache for arenaId=" + arenaId, error);
        }
    }

    public synchronized void put(long arenaId, Optional<CardInfo> card) {
        String sql = """
                MERGE INTO arena_card_cache (arena_id, found, card_json, updated_at, cache_version)
                KEY (arena_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, arenaId);
            statement.setBoolean(2, card.isPresent());
            statement.setString(3, card.map(gson::toJson).orElse(null));
            statement.setInt(4, CURRENT_CACHE_VERSION);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not write card cache for arenaId=" + arenaId, error);
        }
    }

    public synchronized void delete(long arenaId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM arena_card_cache WHERE arena_id = ?")) {
            statement.setLong(1, arenaId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not invalidate card cache for arenaId=" + arenaId, error);
        }
    }

    public synchronized Optional<List<CardInfo>> findSet(
            String setCode,
            Duration maximumAge) {
        String sql = """
                SELECT cards_json, updated_at
                FROM arena_set_cache
                WHERE set_code = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeSetCode(setCode));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                Instant updatedAt = result.getTimestamp("updated_at").toInstant();
                if (updatedAt.isBefore(Instant.now().minus(maximumAge))) {
                    return Optional.empty();
                }
                CardInfo[] cards = gson.fromJson(
                        result.getString("cards_json"), CardInfo[].class);
                return Optional.of(cards == null ? List.of() : List.of(cards));
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Could not read card cache for set=" + setCode, error);
        }
    }

    /**
     * Persists the complete set response and feeds Arena-addressable cards into
     * the same per-card cache used by log enrichment.
     */
    public synchronized void putSet(String setCode, List<CardInfo> cards) {
        List<CardInfo> snapshot = List.copyOf(cards == null ? List.of() : cards);
        String sql = """
                MERGE INTO arena_set_cache (set_code, cards_json, updated_at)
                KEY (set_code) VALUES (?, ?, CURRENT_TIMESTAMP)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeSetCode(setCode));
            statement.setString(2, gson.toJson(snapshot));
            statement.executeUpdate();
            for (CardInfo card : snapshot) {
                if (card != null && card.getArenaId() != null
                        && card.getArenaId() > 0) {
                    put(card.getArenaId(), Optional.of(card));
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Could not write card cache for set=" + setCode, error);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arena_card_cache (
                        arena_id BIGINT PRIMARY KEY,
                        found BOOLEAN NOT NULL,
                        card_json CLOB,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        cache_version INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            // Migrates databases created by revisions before full Scryfall Card objects were retained.
            statement.executeUpdate("ALTER TABLE arena_card_cache ADD COLUMN IF NOT EXISTS cache_version INTEGER NOT NULL DEFAULT 1");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arena_set_cache (
                        set_code VARCHAR(16) PRIMARY KEY,
                        cards_json CLOB NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
        }
    }

    private String normalizeSetCode(String setCode) {
        if (setCode == null || setCode.isBlank()) {
            throw new IllegalArgumentException("setCode is empty");
        }
        return setCode.strip().toLowerCase();
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not close H2 card cache", error);
        }
    }

    public record CachedCard(boolean found, Optional<CardInfo> card, Instant updatedAt, int cacheVersion) {}
}
