package app.deckplanner.candidate;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AlternateArtResolverTest {
    @TempDir Path temporary;

    @Test void favoritePrintingOverridesCatalogPreferredPrinting() {
        CardInfo first = card("print-a", "oracle-a", "Alpha", 1L);
        CardInfo second = card("print-b", "oracle-a", "Alpha", 2L);
        CatalogFilterIndex index = new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH,
                List.of(new FormatCatalogRepository.CardOutcome(first, "SUCCESS", null),
                        new FormatCatalogRepository.CardOutcome(second, "SUCCESS", null))));
        CardNameRepository names = new CardNameRepository(index, name -> Optional.empty());
        try (PrintingPreferenceRepository preferences =
                     new PrintingPreferenceRepository(temporary.resolve("planner"))) {
            AlternateArtResolver resolver = new AlternateArtResolver(index, names, preferences);
            assertTrue(resolver.resolve("oracle:oracle-a").legal());
            resolver.favorite("oracle:oracle-a", first);
            assertEquals("print-a", resolver.resolve("oracle:oracle-a").preferred().getId());
        }
    }

    private static CardInfo card(String id, String oracle, String name, long arenaId) {
        CardInfo card = new CardInfo();
        card.setId(id); card.setOracleId(oracle); card.setName(name); card.setArenaId(arenaId);
        card.setColors(List.of("U")); card.setColorIdentity(List.of("U"));
        card.setTypeLine("Creature — Wizard"); card.setCmc(2.0);
        return card;
    }
}
