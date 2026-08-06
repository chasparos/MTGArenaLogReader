package app.deckplanner.catalog;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stages resumable catalog refreshes and atomically publishes complete snapshots. */
public final class FormatCatalogRepository implements AutoCloseable {
    public static final int SCHEMA_VERSION = 1;

    private final Gson gson;
    private final Connection connection;

    public FormatCatalogRepository(Gson gson, Path databasePath) {
        this.gson = gson;
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            initializeSchema();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize format catalog", error);
        }
    }

    public synchronized Run beginOrResume(String format) {
        String normalized = normalizeFormat(format);
        Optional<Run> existing = activeRun(normalized);
        if (existing.isPresent()) return existing.get();
        String id = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO format_catalog_run
                    (run_id, format_name, schema_version, state, next_cursor,
                     started_at, updated_at)
                VALUES (?, ?, ?, 'STAGING', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, id);
            statement.setString(2, normalized);
            statement.setInt(3, SCHEMA_VERSION);
            statement.executeUpdate();
            return new Run(id, normalized, null, 0);
        } catch (SQLException error) {
            throw failure("Could not start format catalog refresh", error);
        }
    }

    public synchronized void stageSuccess(String runId, CardInfo card) {
        if (card == null || card.getId() == null || card.getId().isBlank()) {
            throw new IllegalArgumentException("Catalog card has no Scryfall identity");
        }
        stage(runId, card.getId(), card, "SUCCESS", null);
    }

    public synchronized void stageFailure(String runId, CardInfo card, Throwable error) {
        String identity = card != null && card.getId() != null && !card.getId().isBlank()
                ? card.getId() : "unidentified-" + UUID.randomUUID();
        stage(runId, identity, card, "FAILED",
                error == null ? "Unknown enrichment failure" : error.toString());
    }

    public synchronized void checkpoint(String runId, String nextCursor) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE format_catalog_run
                SET next_cursor = ?, updated_at = CURRENT_TIMESTAMP
                WHERE run_id = ? AND state = 'STAGING'
                """)) {
            statement.setString(1, nextCursor);
            statement.setString(2, runId);
            requireOne(statement.executeUpdate(), "checkpoint staging run");
        } catch (SQLException error) {
            throw failure("Could not checkpoint format catalog refresh", error);
        }
    }

    public synchronized Snapshot publish(String runId) {
        try {
            connection.setAutoCommit(false);
            Run run = run(runId).orElseThrow(() ->
                    new IllegalArgumentException("Unknown catalog run " + runId));
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE format_catalog_run
                    SET state = 'COMPLETE', next_cursor = NULL,
                        completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE run_id = ? AND state = 'STAGING'
                    """)) {
                update.setString(1, runId);
                requireOne(update.executeUpdate(), "publish staging run");
            }
            try (PreparedStatement current = connection.prepareStatement("""
                    MERGE INTO format_catalog_current (format_name, run_id)
                    KEY (format_name) VALUES (?, ?)
                    """)) {
                current.setString(1, run.format());
                current.setString(2, runId);
                current.executeUpdate();
            }
            connection.commit();
            return snapshot(runId).orElseThrow();
        } catch (Exception error) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            if (error instanceof RuntimeException runtime) throw runtime;
            throw failure("Could not publish format catalog", error);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    public synchronized Optional<Snapshot> current(String format) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id FROM format_catalog_current WHERE format_name = ?
                """)) {
            statement.setString(1, normalizeFormat(format));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? snapshot(result.getString(1)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("Could not read current format catalog", error);
        }
    }

    private Optional<Run> activeRun(String format) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT run_id, next_cursor,
                       (SELECT COUNT(*) FROM format_catalog_card c
                        WHERE c.run_id = r.run_id) AS card_count
                FROM format_catalog_run r
                WHERE format_name = ? AND state = 'STAGING'
                ORDER BY started_at DESC LIMIT 1
                """)) {
            statement.setString(1, format);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new Run(result.getString("run_id"), format,
                        result.getString("next_cursor"), result.getInt("card_count")));
            }
        } catch (SQLException error) {
            throw failure("Could not resume format catalog refresh", error);
        }
    }

    private Optional<Run> run(String runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT format_name, next_cursor,
                       (SELECT COUNT(*) FROM format_catalog_card c
                        WHERE c.run_id = r.run_id) AS card_count
                FROM format_catalog_run r WHERE run_id = ?
                """)) {
            statement.setString(1, runId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new Run(runId, result.getString("format_name"),
                        result.getString("next_cursor"), result.getInt("card_count")));
            }
        }
    }

    private Optional<Snapshot> snapshot(String runId) throws SQLException {
        try (PreparedStatement metadata = connection.prepareStatement("""
                SELECT format_name, schema_version, started_at, completed_at
                FROM format_catalog_run
                WHERE run_id = ? AND state = 'COMPLETE'
                """)) {
            metadata.setString(1, runId);
            try (ResultSet result = metadata.executeQuery()) {
                if (!result.next()) return Optional.empty();
                String format = result.getString("format_name");
                int schemaVersion = result.getInt("schema_version");
                Instant started = result.getTimestamp("started_at").toInstant();
                Instant completed = result.getTimestamp("completed_at").toInstant();
                List<CardOutcome> outcomes = new ArrayList<>();
                try (PreparedStatement cards = connection.prepareStatement("""
                        SELECT card_json, outcome, error_text
                        FROM format_catalog_card WHERE run_id = ? ORDER BY sequence_no
                        """)) {
                    cards.setString(1, runId);
                    try (ResultSet rows = cards.executeQuery()) {
                        while (rows.next()) {
                            String json = rows.getString("card_json");
                            outcomes.add(new CardOutcome(
                                    json == null ? null : gson.fromJson(json, CardInfo.class),
                                    rows.getString("outcome"), rows.getString("error_text")));
                        }
                    }
                }
                return Optional.of(new Snapshot(runId, format, schemaVersion,
                        started, completed, List.copyOf(outcomes)));
            }
        }
    }

    private void stage(String runId, String identity, CardInfo card,
                       String outcome, String error) {
        String sql = """
                MERGE INTO format_catalog_card
                    (run_id, scryfall_id, sequence_no, card_json, outcome, error_text)
                KEY (run_id, scryfall_id)
                VALUES (?, ?, COALESCE((SELECT sequence_no FROM format_catalog_card
                    WHERE run_id = ? AND scryfall_id = ?),
                    (SELECT COALESCE(MAX(sequence_no), 0) + 1
                     FROM format_catalog_card WHERE run_id = ?)), ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, identity);
            statement.setString(3, runId);
            statement.setString(4, identity);
            statement.setString(5, runId);
            statement.setString(6, card == null ? null : gson.toJson(card));
            statement.setString(7, outcome);
            statement.setString(8, error);
            statement.executeUpdate();
        } catch (SQLException sqlError) {
            throw failure("Could not stage catalog card " + identity, sqlError);
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS format_catalog_run (
                        run_id VARCHAR(36) PRIMARY KEY,
                        format_name VARCHAR(32) NOT NULL,
                        schema_version INTEGER NOT NULL,
                        state VARCHAR(16) NOT NULL,
                        next_cursor CLOB,
                        started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        completed_at TIMESTAMP WITH TIME ZONE)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS format_catalog_card (
                        run_id VARCHAR(36) NOT NULL,
                        scryfall_id VARCHAR(128) NOT NULL,
                        sequence_no INTEGER NOT NULL,
                        card_json CLOB,
                        outcome VARCHAR(16) NOT NULL,
                        error_text CLOB,
                        PRIMARY KEY (run_id, scryfall_id))
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS format_catalog_current (
                        format_name VARCHAR(32) PRIMARY KEY,
                        run_id VARCHAR(36) NOT NULL)
                    """);
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || !format.strip().matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid Scryfall format: " + format);
        }
        return format.strip().toLowerCase();
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException("Could not " + action);
    }

    private IllegalStateException failure(String message, Exception error) {
        return new IllegalStateException(message, error);
    }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) { throw failure("Could not close format catalog", error); }
    }

    public record Run(String id, String format, String nextCursor, int stagedCards) { }
    public record CardOutcome(CardInfo card, String outcome, String error) { }
    public record Snapshot(String id, String format, int schemaVersion,
                           Instant startedAt, Instant completedAt,
                           List<CardOutcome> outcomes) {
        public List<CardGroup> cardGroups() {
            Map<String, List<CardInfo>> grouped = new LinkedHashMap<>();
            outcomes.stream()
                    .filter(outcome -> "SUCCESS".equals(outcome.outcome()))
                    .map(CardOutcome::card)
                    .filter(java.util.Objects::nonNull)
                    .forEach(card -> grouped.computeIfAbsent(
                            CatalogCardIdentity.of(card), ignored -> new ArrayList<>()).add(card));
            return grouped.entrySet().stream()
                    .map(entry -> new CardGroup(entry.getKey(),
                            preferredPrinting(entry.getValue()), List.copyOf(entry.getValue())))
                    .toList();
        }

        private static CardInfo preferredPrinting(List<CardInfo> printings) {
            return printings.stream().max(Comparator
                    .comparing((CardInfo card) -> card.getArenaId() != null)
                    .thenComparing(card -> card.getReleasedAt() == null ? "" : card.getReleasedAt())
                    .thenComparing(card -> card.getId() == null ? "" : card.getId()))
                    .orElseThrow();
        }
    }

    public record CardGroup(String identity, CardInfo preferredPrinting,
                            List<CardInfo> printings) { }
}
