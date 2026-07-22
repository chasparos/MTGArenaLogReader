package app.coaching.model;

import java.time.Instant;

/**
 * One persisted message in the chronological OpenAI coaching conversation.
 */
public record CoachingMessage(
        long id,
        long conversationId,
        Role role,
        String content,
        Instant createdAt) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
