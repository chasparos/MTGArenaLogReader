package app.coaching.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable persisted coaching context for one reconstructed Arena match.
 */
public record CoachingConversation(
        long id,
        String matchId,
        String reconstructionSchema,
        String reconstruction,
        Instant createdAt,
        Instant updatedAt,
        List<CoachingGame> games,
        List<CoachingMessage> messages) {

    public CoachingConversation {
        games = games == null ? List.of() : List.copyOf(games);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
