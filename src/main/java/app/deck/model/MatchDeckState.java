package app.deck.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns deck information whose lifetime spans one Arena match.
 *
 * <p>The selected deck is captured once for the match. Each game receives an
 * independent configuration seeded from that selection, allowing later
 * sideboarding reconstruction to replace a game's configuration without
 * changing the registered selection or earlier games.</p>
 */
public final class MatchDeckState {
    private final String matchId;
    private CachedDeck selectedDeck;
    private final Map<Integer, CachedDeck> gameDecks = new LinkedHashMap<>();

    public MatchDeckState(String matchId, CachedDeck selectedDeck) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.selectedDeck = snapshot(selectedDeck);
    }

    public String matchId() {
        return matchId;
    }

    public synchronized CachedDeck selectedDeck() {
        return selectedDeck;
    }

    public synchronized CachedDeck deckForGame(int gameNumber) {
        if (gameNumber <= 0) throw new IllegalArgumentException("gameNumber must be positive");
        if (selectedDeck == null) return null;
        return gameDecks.computeIfAbsent(gameNumber, ignored -> snapshot(selectedDeck));
    }

    /**
     * Replaces only the specified game's active configuration.
     */
    public synchronized void setDeckForGame(int gameNumber, CachedDeck deck) {
        if (gameNumber <= 0) throw new IllegalArgumentException("gameNumber must be positive");
        if (deck == null) gameDecks.remove(gameNumber);
        else gameDecks.put(gameNumber, snapshot(deck));
    }

    /**
     * Refreshes cached card metadata without changing the selected deck identity.
     *
     * <p>Game configurations still matching the previous selected deck are
     * refreshed too. A future sideboarded configuration with a different deck
     * composition remains untouched.</p>
     */
    public synchronized void refreshSelectedDeck(CachedDeck refreshed) {
        if (refreshed == null || selectedDeck == null
                || !Objects.equals(selectedDeck.deckId(), refreshed.deckId())) {
            return;
        }

        CachedDeck previous = selectedDeck;
        selectedDeck = snapshot(refreshed);
        gameDecks.replaceAll((gameNumber, deck) ->
                sameComposition(deck, previous) ? snapshot(refreshed) : deck);
    }

    public synchronized Map<Integer, CachedDeck> gameDeckSnapshot() {
        return Map.copyOf(gameDecks);
    }

    private static boolean sameComposition(CachedDeck left, CachedDeck right) {
        return left != null && right != null
                && left.mainDeck().equals(right.mainDeck())
                && left.sideboard().equals(right.sideboard())
                && left.commandZone().equals(right.commandZone())
                && left.companions().equals(right.companions());
    }

    private static CachedDeck snapshot(CachedDeck deck) {
        if (deck == null) return null;
        return new CachedDeck(
                deck.deckId(),
                deck.name(),
                deck.format(),
                deck.eventName(),
                deck.updatedAt(),
                copy(deck.mainDeck()),
                copy(deck.sideboard()),
                copy(deck.commandZone()),
                copy(deck.companions()));
    }

    private static List<DeckEntry> copy(List<DeckEntry> entries) {
        return entries == null ? List.of() : List.copyOf(entries);
    }
}
