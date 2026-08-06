package app.deckplanner.collection;

import app.model.log.RawLogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaCollectionObserverTest {
    @TempDir Path temporary;

    @Test
    void unrelatedAndDeltaLikeRecordsCannotReplaceCompleteSnapshot() {
        try (ArenaCollectionRepository repository =
                     new ArenaCollectionRepository(temporary.resolve("collection"))) {
            ArenaCollectionObserver observer = new ArenaCollectionObserver(
                    new ArenaCollectionLogParser(), repository);
            observer.accept(raw(1, "{\"1001\":4,\"1002\":1}"));
            observer.accept(raw(2, "{\"InventoryInfo\":{\"Gold\":100}}"));
            observer.accept(raw(3, "{\"1001\":-1}"));
            observer.accept(raw(4, "{}"));

            assertEquals(1, repository.quantity(1002).copies());
            assertEquals(1, repository.current().orElseThrow().sourceSequence());
        }
    }

    private RawLogEntry raw(long sequence, String text) {
        return new RawLogEntry(sequence, Instant.now(), text);
    }
}
