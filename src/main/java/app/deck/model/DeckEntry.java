package app.deck.model;

import app.model.card.CardInfo;

/**
 * Represents or implements DeckEntry in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker data model, which remains separate from canonical replay reconstruction.</p>
 */
public record DeckEntry(long arenaId, int quantity, CardInfo card) {
    public String displayName() {
        return card != null && card.getName() != null ? card.getName() : "ArenaCard#" + arenaId;
    }
}
