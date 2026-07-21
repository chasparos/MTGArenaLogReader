package app.model.game;

import lombok.Data;

/** Human-facing result reconstructed from Arena gameInfo plus the final canonical state. */
@Data
/**
 * Represents GameResult within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class GameResult {
    public enum Reason { DAMAGE, POISON, EMPTY_LIBRARY, CONCEDE, EFFECT, OTHER, DRAW, UNKNOWN }
    public enum Confidence { EXPLICIT, CORRELATED, INFERRED }

    private Integer winnerSeatId;
    private String winnerName;
    private Integer loserSeatId;
    private String loserName;
    private Reason reason = Reason.UNKNOWN;
    private Confidence confidence = Confidence.INFERRED;
    private String finishingCard;
}
