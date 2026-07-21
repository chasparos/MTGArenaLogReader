package app.model.event;

/**
 * Classifies semantic replay events without coupling reconstruction to presentation.
 */
public enum GameEventType {
    GAMEPLAY,
    MATCH_STARTED,
    GAME_STARTED,
    GAME_RESULT,
    MATCH_SCORE,
    MATCH_RESULT
}
