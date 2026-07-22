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
        List<CoachingMessage> messages) {

    public CoachingConversation {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
