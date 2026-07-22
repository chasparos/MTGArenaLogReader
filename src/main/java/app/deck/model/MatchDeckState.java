package app.deck.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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


    /**
     * Applies a complete observed configuration to one game and returns the
     * exact main-deck delta from the preceding game's configuration.
     */
    public synchronized Optional<SideboardChange> observeDeckForGame(
            int gameNumber, CachedDeck observedDeck) {
        if (gameNumber <= 0) throw new IllegalArgumentException("gameNumber must be positive");
        if (observedDeck == null || selectedDeck == null) return Optional.empty();
        if (!Objects.equals(selectedDeck.deckId(), observedDeck.deckId())) return Optional.empty();

        CachedDeck observed = snapshot(observedDeck);
        if (gameNumber == 1) {
            gameDecks.put(1, observed);
            return Optional.empty();
        }

        CachedDeck previous = gameDecks.get(gameNumber - 1);
        gameDecks.put(gameNumber, observed);
        if (previous == null) return Optional.empty();

        SideboardChange change = difference(previous, observed, gameNumber);
        return change.changed() ? Optional.of(change) : Optional.empty();
    }

    public synchronized Map<Integer, CachedDeck> gameDeckSnapshot() {
        return Map.copyOf(gameDecks);
    }



    private SideboardChange difference(CachedDeck previous, CachedDeck observed, int gameNumber) {
        Map<Long, Integer> before = quantities(previous.mainDeck());
        Map<Long, Integer> after = quantities(observed.mainDeck());
        Map<Long, DeckEntry> entries = entriesById(previous, observed);
        List<DeckEntry> broughtIn = new ArrayList<>();
        List<DeckEntry> removed = new ArrayList<>();

        Set<Long> ids = new TreeSet<>();
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());
        for (Long id : ids) {
            int delta = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
            if (delta > 0) broughtIn.add(copyWithQuantity(entries.get(id), delta));
            if (delta < 0) removed.add(copyWithQuantity(entries.get(id), -delta));
        }
        return new SideboardChange(matchId, gameNumber, broughtIn, removed,
                SideboardChange.Confidence.RECONSTRUCTED);
    }

    private static Map<Long, Integer> quantities(List<DeckEntry> entries) {
        Map<Long, Integer> quantities = new TreeMap<>();
        if (entries != null) {
            for (DeckEntry entry : entries) {
                quantities.merge(entry.arenaId(), entry.quantity(), Integer::sum);
            }
        }
        return quantities;
    }

    private static Map<Long, DeckEntry> entriesById(CachedDeck first, CachedDeck second) {
        Map<Long, DeckEntry> entries = new TreeMap<>();
        for (DeckEntry entry : first.mainDeck()) entries.put(entry.arenaId(), entry);
        for (DeckEntry entry : second.mainDeck()) entries.put(entry.arenaId(), entry);
        return entries;
    }

    private static DeckEntry copyWithQuantity(DeckEntry entry, int quantity) {
        return new DeckEntry(entry.arenaId(), quantity, entry.card());
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
