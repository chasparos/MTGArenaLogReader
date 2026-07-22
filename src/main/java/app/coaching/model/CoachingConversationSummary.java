package app.coaching.model;

import java.time.Instant;

/**
 * Lightweight row used by the coaching browser.
 */
public record CoachingConversationSummary(
        long id,
        String matchId,
        Instant createdAt,
        Instant updatedAt,
        int messageCount) {
}
