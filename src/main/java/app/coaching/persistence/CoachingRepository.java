package app.coaching.persistence;

import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingConversationSummary;
import app.coaching.model.CoachingMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * H2-backed persistence for explicitly saved match reconstructions and their
 * complete coaching conversations.
 */
public final class CoachingRepository implements AutoCloseable {
    private final Connection connection;

    public CoachingRepository(Path databasePath) {
        try {
            Path absolute = databasePath.toAbsolutePath();
            if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
            connection = DriverManager.getConnection(
                    "jdbc:h2:file:" + absolute.toString().replace('\\', '/')
                            + ";DB_CLOSE_ON_EXIT=FALSE",
                    "sa",
                    "");
            initialize();
        } catch (Exception error) {
            throw new IllegalStateException("Could not initialize coaching persistence", error);
        }
    }

    public synchronized CoachingConversation saveReconstruction(
            String matchId,
            String reconstructionSchema,
            String reconstruction) {
        requireText(matchId, "matchId");
        requireText(reconstructionSchema, "reconstructionSchema");
        requireText(reconstruction, "reconstruction");

        try {
            Optional<Long> existingId = findConversationId(matchId);
            long id;
            if (existingId.isPresent()) {
                id = existingId.get();
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE coaching_conversation
                           SET reconstruction_schema = ?,
                               reconstruction = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """)) {
                    statement.setString(1, reconstructionSchema);
                    statement.setString(2, reconstruction);
                    statement.setLong(3, id);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO coaching_conversation
                            (match_id, reconstruction_schema, reconstruction, created_at, updated_at)
                        VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, matchId);
                    statement.setString(2, reconstructionSchema);
                    statement.setString(3, reconstruction);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No generated coaching conversation id");
                        }
                        id = keys.getLong(1);
                    }
                }
            }
            return find(id).orElseThrow();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not persist match reconstruction " + matchId, error);
        }
    }

    public synchronized CoachingMessage appendMessage(
            long conversationId,
            CoachingMessage.Role role,
            String content) {
        if (conversationId <= 0) throw new IllegalArgumentException("conversationId must be positive");
        if (role == null) throw new IllegalArgumentException("role is required");
        requireText(content, "content");

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO coaching_message (conversation_id, role_name, content, created_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, conversationId);
            statement.setString(2, role.name());
            statement.setString(3, content);
            statement.executeUpdate();
            long id;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated coaching message id");
                id = keys.getLong(1);
            }
            try (PreparedStatement touch = connection.prepareStatement(
                    "UPDATE coaching_conversation SET updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                touch.setLong(1, conversationId);
                touch.executeUpdate();
            }
            return findMessage(id).orElseThrow();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not persist coaching message", error);
        }
    }

    public synchronized Optional<CoachingConversation> find(long id) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, match_id, reconstruction_schema, reconstruction, created_at, updated_at
                  FROM coaching_conversation
                 WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(readConversation(result, messages(id)));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read coaching conversation " + id, error);
        }
    }

    public synchronized List<CoachingConversationSummary> listConversations() {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.id,
                       c.match_id,
                       c.created_at,
                       c.updated_at,
                       COUNT(m.id) AS message_count
                  FROM coaching_conversation c
                  LEFT JOIN coaching_message m ON m.conversation_id = c.id
                 GROUP BY c.id, c.match_id, c.created_at, c.updated_at
                 ORDER BY c.updated_at DESC, c.id DESC
                """);
             ResultSet result = statement.executeQuery()) {
            List<CoachingConversationSummary> conversations = new ArrayList<>();
            while (result.next()) {
                conversations.add(new CoachingConversationSummary(
                        result.getLong("id"),
                        result.getString("match_id"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant(),
                        result.getInt("message_count")));
            }
            return List.copyOf(conversations);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not list coaching conversations", error);
        }
    }

    private List<CoachingMessage> messages(long conversationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, conversation_id, role_name, content, created_at
                  FROM coaching_message
                 WHERE conversation_id = ?
                 ORDER BY id
                """)) {
            statement.setLong(1, conversationId);
            try (ResultSet result = statement.executeQuery()) {
                List<CoachingMessage> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(readMessage(result));
                }
                return List.copyOf(messages);
            }
        }
    }

    private Optional<Long> findConversationId(String matchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM coaching_conversation WHERE match_id = ?")) {
            statement.setString(1, matchId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getLong(1)) : Optional.empty();
            }
        }
    }

    private Optional<CoachingMessage> findMessage(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, conversation_id, role_name, content, created_at
                  FROM coaching_message
                 WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readMessage(result)) : Optional.empty();
            }
        }
    }

    private CoachingConversation readConversation(ResultSet result, List<CoachingMessage> messages)
            throws SQLException {
        return new CoachingConversation(
                result.getLong("id"),
                result.getString("match_id"),
                result.getString("reconstruction_schema"),
                result.getString("reconstruction"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                messages);
    }

    private CoachingMessage readMessage(ResultSet result) throws SQLException {
        return new CoachingMessage(
                result.getLong("id"),
                result.getLong("conversation_id"),
                CoachingMessage.Role.valueOf(result.getString("role_name")),
                result.getString("content"),
                result.getTimestamp("created_at").toInstant());
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS coaching_conversation (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        match_id VARCHAR NOT NULL UNIQUE,
                        reconstruction_schema VARCHAR NOT NULL,
                        reconstruction CLOB NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS coaching_message (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        conversation_id BIGINT NOT NULL,
                        role_name VARCHAR NOT NULL,
                        content CLOB NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        CONSTRAINT fk_coaching_message_conversation
                            FOREIGN KEY (conversation_id)
                            REFERENCES coaching_conversation(id)
                            ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_coaching_message_conversation
                        ON coaching_message(conversation_id, id)
                    """);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException error) {
            throw new IllegalStateException("Could not close coaching persistence", error);
        }
    }
}
