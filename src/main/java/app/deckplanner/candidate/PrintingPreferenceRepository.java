package app.deckplanner.candidate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Optional;

/** Persists the user's favorite Scryfall printing for each logical candidate identity. */
public final class PrintingPreferenceRepository implements AutoCloseable {
    private final Connection connection;

    public PrintingPreferenceRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS deck_planner_favorite_printing (
                            card_identity VARCHAR(160) PRIMARY KEY,
                            scryfall_id VARCHAR(80) NOT NULL
                        )
                        """);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize favorite printing repository", error);
        }
    }

    public synchronized Optional<String> favorite(String identity) {
        if (identity == null || identity.isBlank()) return Optional.empty();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT scryfall_id FROM deck_planner_favorite_printing WHERE card_identity = ?")) {
            statement.setString(1, identity);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.ofNullable(rows.getString(1)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read favorite printing", error);
        }
    }

    public synchronized void setFavorite(String identity, String scryfallId) {
        if (identity == null || identity.isBlank() || scryfallId == null || scryfallId.isBlank()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO deck_planner_favorite_printing (card_identity, scryfall_id)
                KEY(card_identity) VALUES (?, ?)
                """)) {
            statement.setString(1, identity);
            statement.setString(2, scryfallId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not save favorite printing", error);
        }
    }

    @Override public synchronized void close() {
        try { connection.close(); }
        catch (SQLException error) { throw new IllegalStateException("Could not close favorite printing repository", error); }
    }
}
