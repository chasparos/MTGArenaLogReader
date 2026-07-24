package app.model.game;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Player resources captured at the first reliable state update of a turn. */
@Data
/**
 * Represents PlayerTurnSnapshot within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class PlayerTurnSnapshot {
    private int seatId;
    private String playerName;
    private Integer lifeTotal;
    private Integer poisonCounters;
    private Integer handSize;
    private final List<BoardPermanentSnapshot> battlefield = new ArrayList<>();
    /** Cards whose identity is currently known in this player's hand. */
    private final List<app.model.card.CardInfo> knownHand = new ArrayList<>();
    /** Public cards currently known in this player's graveyard. */
    private final List<app.model.card.CardInfo> knownGraveyard = new ArrayList<>();
    /** Public cards currently known in this player's exile zone. */
    private final List<app.model.card.CardInfo> knownExile = new ArrayList<>();
}
