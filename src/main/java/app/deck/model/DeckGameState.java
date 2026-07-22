package app.deck.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents or implements DeckGameState in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker data model, which remains separate from canonical replay reconstruction.</p>
 */
public record DeckGameState(
        String matchId,
        int gameNumber,
        int turnNumber,
        CachedDeck deck,
        int libraryCount,
        int graveyardCount,
        int exileCount,
        Map<Long,Integer> knownOutsideLibrary,
        boolean complete
) {
    public DeckGameState {
        knownOutsideLibrary = knownOutsideLibrary == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(knownOutsideLibrary));
    }

    public int remainingCopies(long arenaId, int originalQuantity) {
        return Math.max(0, originalQuantity - knownOutsideLibrary.getOrDefault(arenaId, 0));
    }

    public double drawPercent(long arenaId, int originalQuantity) {
        if (libraryCount <= 0) return 0.0;
        return 100.0 * remainingCopies(arenaId, originalQuantity) / libraryCount;
    }
}
