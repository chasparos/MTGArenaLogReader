package app.model.game;

import lombok.Data;

import java.util.Objects;

@Data
/**
 * Represents GameKey within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public final class GameKey {
    private final String matchId;
    private final int gameNumber;

    public GameKey(String matchId, int gameNumber) {
        this.matchId = Objects.requireNonNullElse(matchId, "unknown-match");
        this.gameNumber = Math.max(1, gameNumber);
    }

    public String displayName() {
        String shortMatch = matchId.length() > 8 ? matchId.substring(0, 8) : matchId;
        return "Game " + gameNumber + " · " + shortMatch;
    }
}
