package app.deck.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchDeckStateTest {
    @Test
    void selectedDeckIsCapturedAsAnImmutableMatchSnapshot() {
        List<DeckEntry> mainDeck = new ArrayList<>();
        mainDeck.add(entry(100, 4));
        CachedDeck selected = deck("deck-1", mainDeck, List.of(entry(200, 2)));

        MatchDeckState state = new MatchDeckState("match-1", selected);
        mainDeck.add(entry(300, 1));

        assertEquals(1, state.selectedDeck().mainDeck().size());
        assertThrows(UnsupportedOperationException.class,
                () -> state.selectedDeck().mainDeck().add(entry(400, 1)));
    }

    @Test
    void changingOneGameConfigurationDoesNotAffectOtherGamesOrSelection() {
        CachedDeck selected = deck("deck-1", List.of(entry(100, 4)), List.of(entry(200, 2)));
        CachedDeck sideboarded = deck("deck-1", List.of(entry(100, 3), entry(200, 1)),
                List.of(entry(100, 1), entry(200, 1)));

        MatchDeckState state = new MatchDeckState("match-1", selected);
        CachedDeck gameOne = state.deckForGame(1);
        state.setDeckForGame(2, sideboarded);

        assertEquals(selected.mainDeck(), state.selectedDeck().mainDeck());
        assertEquals(gameOne.mainDeck(), state.deckForGame(1).mainDeck());
        assertEquals(sideboarded.mainDeck(), state.deckForGame(2).mainDeck());
    }

    @Test
    void metadataRefreshDoesNotOverwriteChangedGameConfiguration() {
        CachedDeck selected = deck("deck-1", List.of(entry(100, 4)), List.of(entry(200, 2)));
        CachedDeck sideboarded = deck("deck-1", List.of(entry(100, 3), entry(200, 1)),
                List.of(entry(100, 1), entry(200, 1)));
        CachedDeck enriched = new CachedDeck("deck-1", "Enriched", "Standard", "event",
                Instant.parse("2026-01-02T00:00:00Z"), selected.mainDeck(), selected.sideboard(),
                List.of(), List.of());

        MatchDeckState state = new MatchDeckState("match-1", selected);
        state.deckForGame(1);
        state.setDeckForGame(2, sideboarded);
        state.refreshSelectedDeck(enriched);

        assertEquals("Enriched", state.selectedDeck().name());
        assertEquals("Enriched", state.deckForGame(1).name());
        assertEquals(sideboarded.mainDeck(), state.deckForGame(2).mainDeck());
        assertNotEquals("Enriched", state.deckForGame(2).name());
    }

    private static CachedDeck deck(String id, List<DeckEntry> main, List<DeckEntry> sideboard) {
        return new CachedDeck(id, "Selected", "Standard", "event",
                Instant.parse("2026-01-01T00:00:00Z"), main, sideboard, List.of(), List.of());
    }

    private static DeckEntry entry(long arenaId, int quantity) {
        return new DeckEntry(arenaId, quantity, null);
    }
}
