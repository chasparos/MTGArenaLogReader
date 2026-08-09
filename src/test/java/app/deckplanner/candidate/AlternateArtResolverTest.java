package app.deckplanner.candidate;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import app.model.card.CardImageUris;
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

    @Test void cachedResolutionDoesNotInvokeRemotePrintingEnumerationAndDefaultsToFirstLocalPrinting() {
        CardInfo first = card("print-a", "oracle-a", "Alpha", 1L);
        CardInfo second = card("print-b", "oracle-a", "Alpha", 2L);
        CatalogFilterIndex index = new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH,
                List.of(new FormatCatalogRepository.CardOutcome(first, "SUCCESS", null),
                        new FormatCatalogRepository.CardOutcome(second, "SUCCESS", null))));
        java.util.concurrent.atomic.AtomicInteger remoteCalls = new java.util.concurrent.atomic.AtomicInteger();
        CardNameRepository names = new CardNameRepository(index, null, name -> Optional.empty(),
                name -> {
                    remoteCalls.incrementAndGet();
                    return List.of(second);
                });
        try (PrintingPreferenceRepository preferences =
                     new PrintingPreferenceRepository(temporary.resolve("cached-only"))) {
            AlternateArtResolver resolver = new AlternateArtResolver(index, names, preferences);
            AlternateArtResolver.ArtSet cached = resolver.resolveCached("oracle:oracle-a");
            assertFalse(cached.complete());
            assertEquals(0, remoteCalls.get());
            assertEquals("print-a", cached.preferred().getId(),
                    "without an explicit favorite the first local/cached printing stays stable");
        }
    }


    @Test void cachedResolutionPrefersFirstImageCapablePrintingWhenNoFavoriteExists() {
        CardInfo metadataOnly = card("print-a", "oracle-a", "Alpha", 1L);
        CardInfo renderable = card("print-b", "oracle-a", "Alpha", 2L);
        CardImageUris images = new CardImageUris();
        images.setNormal("https://example.invalid/alpha.jpg");
        renderable.setImageUris(images);
        CatalogFilterIndex index = new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH,
                List.of(new FormatCatalogRepository.CardOutcome(metadataOnly, "SUCCESS", null),
                        new FormatCatalogRepository.CardOutcome(renderable, "SUCCESS", null))));
        CardNameRepository names = new CardNameRepository(index, name -> Optional.empty());
        try (PrintingPreferenceRepository preferences =
                     new PrintingPreferenceRepository(temporary.resolve("renderable-default"))) {
            AlternateArtResolver resolver = new AlternateArtResolver(index, names, preferences);
            assertEquals("print-b", resolver.resolveCached("oracle:oracle-a").preferred().getId(),
                    "metadata-only cached printings must not shadow a renderable default");
        }
    }


    @Test void favoriteUsesRenderableCopyWhenSamePrintingExistsAsMetadataAndEnrichedCard() {
        CardInfo metadataOnly = card("print-last", "oracle-a", "Alpha", 2L);
        CardInfo enriched = card("print-last", "oracle-a", "Alpha", 2L);
        CardImageUris images = new CardImageUris();
        images.setNormal("https://example.invalid/alpha-last.jpg");
        enriched.setImageUris(images);

        AlternateArtResolver.ArtSet artSet = new AlternateArtResolver.ArtSet(
                "oracle:oracle-a", true, List.of(metadataOnly, enriched),
                Optional.of("print-last"), true);

        assertSame(enriched, artSet.preferred(),
                "favorite selection must keep the image-capable copy of the chosen printing");
    }

    @Test void favoriteCanSelectLastPrintingWithoutIndexSpecialCases() {
        CardInfo first = card("print-a", "oracle-a", "Alpha", 1L);
        CardInfo last = card("print-z", "oracle-a", "Alpha", 9L);
        CardImageUris images = new CardImageUris();
        images.setNormal("https://example.invalid/alpha-z.jpg");
        last.setImageUris(images);

        AlternateArtResolver.ArtSet artSet = new AlternateArtResolver.ArtSet(
                "oracle:oracle-a", true, List.of(first, last),
                Optional.of("print-z"), true);

        assertSame(last, artSet.preferred());
    }

    private static CardInfo card(String id, String oracle, String name, long arenaId) {
        CardInfo card = new CardInfo();
        card.setId(id); card.setOracleId(oracle); card.setName(name); card.setArenaId(arenaId);
        card.setColors(List.of("U")); card.setColorIdentity(List.of("U"));
        card.setTypeLine("Creature — Wizard"); card.setCmc(2.0);
        return card;
    }
}
