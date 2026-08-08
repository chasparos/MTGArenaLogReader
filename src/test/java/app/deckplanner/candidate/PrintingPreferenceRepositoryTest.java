package app.deckplanner.candidate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrintingPreferenceRepositoryTest {
    @TempDir Path temporary;

    @Test void favoritePrintingPersistsPerLogicalIdentity() {
        Path database = temporary.resolve("planner");
        try (PrintingPreferenceRepository repository = new PrintingPreferenceRepository(database)) {
            assertTrue(repository.favorite("oracle:alpha").isEmpty());
            repository.setFavorite("oracle:alpha", "printing-two");
            assertEquals("printing-two", repository.favorite("oracle:alpha").orElseThrow());
        }
        try (PrintingPreferenceRepository repository = new PrintingPreferenceRepository(database)) {
            assertEquals("printing-two", repository.favorite("oracle:alpha").orElseThrow());
        }
    }
}
