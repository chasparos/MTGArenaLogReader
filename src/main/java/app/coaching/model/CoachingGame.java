package app.coaching.model;

/**
 * Human-readable reconstruction of one game persisted with its coaching match.
 */
public record CoachingGame(
        int gameNumber,
        String reconstruction) {
}
