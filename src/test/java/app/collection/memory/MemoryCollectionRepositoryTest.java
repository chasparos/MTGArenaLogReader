package app.collection.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCollectionRepositoryTest {
    @TempDir Path temporary;

    @Test
    void distinguishesUnknownKnownZeroAndPositiveCopiesAcrossReopen() {
        Path database = temporary.resolve("ownership");
        try (MemoryCollectionRepository repository = new MemoryCollectionRepository(database)) {
            assertEquals(-1, repository.getCopiesOwned(1001));
            repository.replaceComplete(Map.of(1001L, 4));
            assertEquals(4, repository.getCopiesOwned(1001));
            assertEquals(0, repository.getCopiesOwned(9999));
        }
        try (MemoryCollectionRepository reopened = new MemoryCollectionRepository(database)) {
            assertEquals(4, reopened.getCopiesOwned(1001));
            assertEquals(0, reopened.getCopiesOwned(9999));
        }
    }

    @Test
    void rejectsInvalidReplacementBeforeChangingTheCompleteTable() {
        try (MemoryCollectionRepository repository =
                     new MemoryCollectionRepository(temporary.resolve("atomic"))) {
            repository.replaceComplete(Map.of(1001L, 3));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.replaceComplete(Map.of(-5L, 2)));
            assertEquals(3, repository.getCopiesOwned(1001));
        }
    }

    @Test
    void persistsVerifiedCardsWithFullPlaysetsPreferred() {
        Path database = temporary.resolve("verified");
        try (MemoryCollectionRepository repository = new MemoryCollectionRepository(database)) {
            repository.replaceVerifiedCards(Map.of(30L, 2, 20L, 4, 10L, 1));
            assertEquals(java.util.List.of(20L, 10L, 30L),
                    repository.verifiedCardsPreferred().keySet().stream().toList());
            assertThrows(IllegalArgumentException.class,
                    () -> repository.replaceVerifiedCards(Map.of(20L, 4)));
            assertEquals(3, repository.verifiedCardsPreferred().size());
        }
    }
}
