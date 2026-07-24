package app.model.game;


import app.model.card.CardInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Immutable-by-convention view of one permanent for a start-of-turn board snapshot. */
@Data
/**
 * Represents BoardPermanentSnapshot within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class BoardPermanentSnapshot {
    private long logicalObjectId;
    private int ownerSeatId;
    private int controllerSeatId;
    private String name;
    private CardInfo card;
    private Boolean tapped;
    private Integer power;
    private Integer toughness;
    /** Logical object id of the permanent this card is attached to, if any. */
    private Long attachedToLogicalObjectId;
    private final List<CounterState> counters = new ArrayList<>();
    /** Human-readable Room halves known to be unlocked on this permanent. */
    private final List<String> unlockedRoomHalves = new ArrayList<>();
    /** Evergreen abilities explicitly known for this permanent instance. */
    private final List<String> evergreenAbilities = new ArrayList<>();
}
