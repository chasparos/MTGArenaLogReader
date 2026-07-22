package app.model.event;

/**
 * Classifies semantic replay events without coupling reconstruction to presentation.
 */
public enum GameEventType {
    GAMEPLAY,
    OPENING_HAND,
    DECISION,
    PLAYER_LIFE_CHANGE,
    PLANESWALKER_DAMAGE,
    MATCH_STARTED,
    GAME_STARTED,
    GAME_RESULT,
    MATCH_SCORE,
    MATCH_RESULT
}
