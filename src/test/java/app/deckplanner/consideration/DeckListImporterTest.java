package app.deckplanner.consideration;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.enrichment.CardCache;
import app.model.card.CardInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckListImporterTest {
    @TempDir Path temporary;
    @Test void importsArenaDeckSectionsByNameAndCollapsesAlternatePrintings() {
        CardInfo optA = card("opt", "Opt", "opt-a");
        CardInfo optB = card("opt", "Opt", "opt-b");
        CardInfo island = card("island", "Island", "island-a");
        CatalogFilterIndex index = index(optA, optB, island);

        DeckListImporter.Result result = DeckListImporter.resolve("""
                Deck
                4 Opt (STA) 19
                20 Island (MOM) 278

                Sideboard
                1 Opt (ELD) 59
                2 Missing Card (ABC) 1
                """, index);

        assertEquals(4, result.parsedCardLines());
        assertEquals(List.of("oracle:opt", "oracle:island"), result.identities());
        assertEquals(List.of("Missing Card"), result.unresolvedNames());
    }


    @Test void fallsBackToExactNameRepositoryWithoutImplyingOwnership() {
        CatalogFilterIndex index = index();
        CardInfo remote = card("remote-oracle", "Remote Card", "remote-printing");
        CardNameRepository names = new CardNameRepository(index,
                exact -> "Remote Card".equals(exact) ? java.util.Optional.of(remote) : java.util.Optional.empty());

        DeckListImporter.Result result = DeckListImporter.resolve("""
                Deck
                2 Remote Card
                1 Missing Card
                """, names);

        assertEquals(List.of("oracle:remote-oracle"), result.identities());
        assertEquals(List.of("Missing Card"), result.unresolvedNames());
        assertEquals(1, result.fallbackCards());
    }



    @Test void persistentCardCacheResolvesPlayedDeckNamesBeforeNetworkFallback() {
        CatalogFilterIndex index = index();
        CardInfo cached = card("cached-oracle", "Played Extensively", "cached-printing");
        AtomicInteger networkLookups = new AtomicInteger();

        try (CardCache cache = new CardCache(new Gson(), temporary.resolve("cards"))) {
            cache.put(cached.getArenaId(), Optional.of(cached));
            CardNameRepository names = new CardNameRepository(
                    index, cache, ignored -> {
                        networkLookups.incrementAndGet();
                        return Optional.empty();
                    });

            DeckListImporter.Result result = DeckListImporter.resolve("""
                    Deck
                    4 Played Extensively
                    """, names);

            assertEquals(List.of("oracle:cached-oracle"), result.identities());
            assertEquals(0, result.fallbackCards());
            assertEquals(0, networkLookups.get());
        }
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String oracleId, String name, String scryfallId) {
        CardInfo card = new CardInfo();
        card.setId(scryfallId);
        card.setOracleId(oracleId);
        card.setName(name);
        card.setArenaId((long) Math.abs(scryfallId.hashCode()) + 1);
        card.setColors(List.of("U"));
        card.setColorIdentity(List.of("U"));
        card.setTypeLine("Instant");
        card.setCmc(1.0);
        card.setOracleText("Rules");
        card.setKeywords(List.of());
        return card;
    }
}
