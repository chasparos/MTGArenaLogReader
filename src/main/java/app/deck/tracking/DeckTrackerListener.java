package app.deck.tracking;


import app.deck.model.DeckGameState;
import app.deck.model.SideboardChange;
/**
 * Represents or implements DeckTrackerListener in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the live deck-tracking subsystem, which consumes observations independently of replay reconstruction.</p>
 */
public interface DeckTrackerListener {
    void gameStarted(DeckGameState state);
    void gameUpdated(DeckGameState state);
    void gameCompleted(String matchId, int gameNumber);

    default void sideboardChanged(SideboardChange change) {}
}
