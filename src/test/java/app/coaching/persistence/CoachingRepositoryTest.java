package app.coaching.persistence;

import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CoachingRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsReconstructionAndConversationAcrossRepositoryRestart() {
        Path database = temporaryDirectory.resolve("coaching");

        long conversationId;
        try (CoachingRepository repository = new CoachingRepository(database)) {
            CoachingConversation conversation = repository.saveReconstruction(
                    "match-1",
                    "MTGA_MATCH_V3",
                    "first reconstruction");
            conversationId = conversation.id();

            repository.appendMessage(
                    conversationId,
                    CoachingMessage.Role.USER,
                    "Where did I lose tempo?");
            repository.appendMessage(
                    conversationId,
                    CoachingMessage.Role.ASSISTANT,
                    "Review event E#42.");
        }

        try (CoachingRepository repository = new CoachingRepository(database)) {
            CoachingConversation restored = repository.find(conversationId).orElseThrow();

            assertEquals("match-1", restored.matchId());
            assertEquals("first reconstruction", restored.reconstruction());
            assertEquals(2, restored.messages().size());
            assertEquals(CoachingMessage.Role.USER, restored.messages().get(0).role());
            assertEquals(CoachingMessage.Role.ASSISTANT, restored.messages().get(1).role());
            assertEquals(1, repository.listConversations().size());
            assertEquals(2, repository.listConversations().getFirst().messageCount());
        }
    }

    @Test
    void refreshingAReconstructionPreservesItsConversation() {
        try (CoachingRepository repository =
                     new CoachingRepository(temporaryDirectory.resolve("coaching-refresh"))) {
            CoachingConversation original = repository.saveReconstruction(
                    "match-2",
                    "MTGA_MATCH_V3",
                    "partial");
            repository.appendMessage(
                    original.id(),
                    CoachingMessage.Role.USER,
                    "Save this question");

            CoachingConversation refreshed = repository.saveReconstruction(
                    "match-2",
                    "MTGA_MATCH_V3",
                    "complete");

            assertEquals(original.id(), refreshed.id());
            assertEquals("complete", refreshed.reconstruction());
            assertEquals(1, refreshed.messages().size());
            assertTrue(refreshed.updatedAt().compareTo(original.updatedAt()) >= 0);
        }
    }
}
