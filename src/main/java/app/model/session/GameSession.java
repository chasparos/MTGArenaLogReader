package app.model.session;

import app.projection.GameEventProjector;

/**
 * Couples one per-game model with its per-game projector.
 */
public record GameSession(GameModel model, GameEventProjector projector) {
}
