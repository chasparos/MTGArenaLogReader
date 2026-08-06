package app.deckplanner.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArenaCollectionRepositoryTest {
    @TempDir Path temporary;

    @Test
    void unknownZeroAndOwnedRemainDistinctAndRoundTrip() {
        try (ArenaCollectionRepository repository = repository()) {
            assertEquals(-1, repository.quantity(1001).copies());
            assertFalse(repository.quantity(1001).known());

            repository.replaceComplete(snapshot(Map.of(1001L, 4, 1002L, 1), 8));

            assertEquals(4, repository.quantity(1001).copies());
            assertTrue(repository.quantity(1001).owned());
            assertEquals(0, repository.quantity(9999).copies());
            assertTrue(repository.quantity(9999).known());
            ArenaCollectionSnapshot restored = repository.current().orElseThrow();
            assertEquals(Map.of(1001L, 4, 1002L, 1), restored.ownedCopies());
            assertEquals(8, restored.sourceSequence());
        }
    }

    @Test
    void laterCompleteSnapshotAtomicallyReplacesAbsenceDomain() {
        try (ArenaCollectionRepository repository = repository()) {
            repository.replaceComplete(snapshot(Map.of(1001L, 4), 1));
            repository.replaceComplete(snapshot(Map.of(1002L, 2), 2));
            assertEquals(0, repository.quantity(1001).copies());
            assertEquals(2, repository.quantity(1002).copies());
            assertEquals(2, repository.current().orElseThrow().sourceSequence());
        }
    }

    @Test
    void freshnessPolicyReturnsUnknownWithoutDestroyingAuditableSnapshot() {
        try (ArenaCollectionRepository repository = repository()) {
            ArenaCollectionSnapshot old = new ArenaCollectionSnapshot(
                    Map.of(1001L, 4), Instant.EPOCH, 1,
                    ArenaCollectionSnapshot.Source.BARE_NUMERIC_CARD_MAP);
            repository.replaceComplete(old);
            assertEquals(-1, repository.quantity(1001, Duration.ofDays(30)).copies());
            assertEquals(4, repository.quantity(1001).copies());
            assertEquals(Instant.EPOCH, repository.current().orElseThrow().observedAt());
        }
    }

    private ArenaCollectionRepository repository() {
        return new ArenaCollectionRepository(temporary.resolve("collection"));
    }

    private ArenaCollectionSnapshot snapshot(Map<Long, Integer> cards, long sequence) {
        return new ArenaCollectionSnapshot(cards, Instant.parse("2026-08-06T12:00:00Z"),
                sequence, ArenaCollectionSnapshot.Source.BARE_NUMERIC_CARD_MAP);
    }
}
