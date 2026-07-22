package app.coaching.model;

/**
 * Persisted representations of one reconstructed game.
 *
 * <p>The human reconstruction remains useful for export and compatibility.
 * The rich snapshot is an application-owned serialization of the semantic
 * game model used to rebuild the read-only replay component.</p>
 */
public record CoachingGame(
        int gameNumber,
        String reconstruction,
        String richSnapshot) {
}
