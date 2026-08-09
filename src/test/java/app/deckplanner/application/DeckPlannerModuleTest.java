package app.deckplanner.application;

import app.deckplanner.catalog.FormatCatalogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckPlannerModuleTest {
    @Test
    void keepsFreshCompletedCatalogCacheFirst() {
        assertFalse(DeckPlannerModule.refreshRecommended(snapshot(Instant.now())));
    }

    @Test
    void refreshesStaleCatalogInBackground() {
        assertTrue(DeckPlannerModule.refreshRecommended(
                snapshot(Instant.now().minusSeconds(25 * 60 * 60))));
    }

    private static FormatCatalogRepository.Snapshot snapshot(Instant completedAt) {
        return new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, completedAt.minusSeconds(60), completedAt, List.of());
    }
}
