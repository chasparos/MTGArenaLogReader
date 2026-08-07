package app.deck.persistence;

import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.enrichment.CardCache;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckCacheSelectionTest {
    @TempDir
    Path tempDir;

    @Test
    void findsMostRecentDeckForEventContainingTheVisibleOpeningHand() {
        Gson gson = new Gson();
        try (CardCache cards = new CardCache(gson, tempDir.resolve("cards"));
             DeckCache decks = new DeckCache(gson, cards, tempDir.resolve("decks"))) {
            decks.put(deck("wrong", "Constructed_BestOf3", 100, 200));
            decks.put(deck("right", "Constructed_BestOf3", 300, 400));

            CachedDeck selected = decks.mostRecentContainingCards(
                    "Constructed_BestOf3", Map.of(300L, 2, 400L, 1)).orElseThrow();

            assertEquals("right", selected.deckId());
        }
    }

    @Test
    void doesNotCrossEventBoundariesDuringValidation() {
        Gson gson = new Gson();
        try (CardCache cards = new CardCache(gson, tempDir.resolve("cards-event"));
             DeckCache decks = new DeckCache(gson, cards, tempDir.resolve("decks-event"))) {
            decks.put(deck("other-event", "Ladder", 300, 400));

            assertTrue(decks.mostRecentContainingCards(
                    "Constructed_BestOf3", Map.of(300L, 1)).isEmpty());
        }
    }


    @Test
    void listsRecentObservedDecksForPlannerSelection() {
        Gson gson = new Gson();
        try (CardCache cards = new CardCache(gson, tempDir.resolve("cards-recent"));
             DeckCache decks = new DeckCache(gson, cards, tempDir.resolve("decks-recent"))) {
            decks.put(deck("first", "Ladder", 100, 200));
            decks.put(deck("second", "Ladder", 300, 400));

            List<CachedDeck> recent = decks.recent(10);

            assertEquals(2, recent.size());
            assertTrue(recent.stream().map(CachedDeck::deckId).toList()
                    .containsAll(List.of("first", "second")));
        }
    }

    private CachedDeck deck(String id, String event, long first, long second) {
        return new CachedDeck(id, id, "Standard", event, Instant.now(),
                List.of(new DeckEntry(first, 4, null), new DeckEntry(second, 4, null)),
                List.of(), List.of(), List.of());
    }
}
