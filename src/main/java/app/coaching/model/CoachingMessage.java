package app.coaching.model;

import java.time.Instant;

/**
 * One persisted message in the chronological coaching conversation.
 */
public record CoachingMessage(
        long id,
        long conversationId,
        Role role,
        String content,
        Instant createdAt,
        CoachingContext context) {

    public CoachingMessage(long id, long conversationId, Role role, String content, Instant createdAt) {
        this(id, conversationId, role, content, createdAt, null);
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }
}
