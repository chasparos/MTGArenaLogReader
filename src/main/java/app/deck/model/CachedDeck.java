package app.deck.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents or implements CachedDeck in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker data model, which remains separate from canonical replay reconstruction.</p>
 */
public record CachedDeck(
        String deckId,
        String name,
        String format,
        String eventName,
        Instant updatedAt,
        List<DeckEntry> mainDeck,
        List<DeckEntry> sideboard,
        List<DeckEntry> commandZone,
        List<DeckEntry> companions
) {
    public int mainDeckSize() {
        return mainDeck.stream().mapToInt(DeckEntry::quantity).sum();
    }
}
