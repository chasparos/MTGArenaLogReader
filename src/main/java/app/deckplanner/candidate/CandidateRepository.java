package app.deckplanner.candidate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persists the user's ordered logical-card candidates workspace. */
public final class CandidateRepository implements AutoCloseable {
    private final Connection connection;

    public CandidateRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            initializeSchema();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize candidates repository", error);
        }
    }

    public synchronized List<String> load() {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT card_identity
                FROM deck_planner_candidates
                ORDER BY position_no
                """);
             ResultSet rows = statement.executeQuery()) {
            List<String> identities = new ArrayList<>();
            while (rows.next()) identities.add(rows.getString(1));
            return List.copyOf(identities);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read candidates workspace", error);
        }
    }

    public synchronized void replace(List<String> identities) {
        List<String> snapshot = normalize(identities);
        try {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM deck_planner_candidates");
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO deck_planner_candidates (position_no, card_identity)
                    VALUES (?, ?)
                    """)) {
                for (int index = 0; index < snapshot.size(); index++) {
                    insert.setInt(1, index);
                    insert.setString(2, snapshot.get(index));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            try { connection.rollback(); } catch (SQLException ignored) { }
            throw new IllegalStateException("Could not persist candidates workspace", error);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) { }
        }
    }

    private static List<String> normalize(List<String> identities) {
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        if (identities != null) {
            for (String identity : identities) {
                if (identity != null && !identity.isBlank()) unique.add(identity.strip());
            }
        }
        return List.copyOf(unique);
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS deck_planner_candidates (
                        position_no INTEGER PRIMARY KEY,
                        card_identity VARCHAR(256) NOT NULL UNIQUE)
                    """);
            // One-way compatibility migration from the pre-rename DP-06 schema.
            try {
                statement.executeUpdate("""
                        INSERT INTO deck_planner_candidates (position_no, card_identity)
                        SELECT position_no, card_identity
                        FROM deck_planner_consideration legacy
                        WHERE NOT EXISTS (
                            SELECT 1 FROM deck_planner_candidates current_rows
                            WHERE current_rows.card_identity = legacy.card_identity)
                        """);
            } catch (SQLException legacyTableAbsent) {
                // Fresh repositories never had the legacy table.
            }
        }
    }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) {
            throw new IllegalStateException("Could not close candidates repository", error);
        }
    }
}
