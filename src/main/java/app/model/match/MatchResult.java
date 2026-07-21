package app.model.match;

/**
 * Reconstructed match outcome attached to a semantic replay event.
 */
public record MatchResult(
        Integer winnerSeatId,
        String winnerName,
        MatchScore finalScore,
        Confidence confidence) {

    public enum Confidence {
        EXPLICIT,
        INFERRED
    }
}
