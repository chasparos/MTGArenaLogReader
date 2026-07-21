package app.model.event;


import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CombatAttackAssignment;
import app.model.game.CombatBlockAssignment;
import app.model.game.GameResult;
import app.model.game.PlayerTurnSnapshot;
import app.model.match.MatchResult;
import app.model.match.MatchScore;
import app.snapshot.BoardStateMonitor;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
/**
 * Represents GameEvent at the immutable semantic-event boundary consumed by replay views and exports.
 *
 * <p>Projection creates these values after applying ordered Arena observations to canonical state.</p>
 *
 * <p>The type must not parse raw GRE records or contain Swing presentation behavior.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the immutable semantic-event boundary consumed by replay views and exporters.</p>
 */
public class GameEvent {
    private GameEventType type = GameEventType.GAMEPLAY;
    private long sequence;
    private Instant timestamp;
    private Integer turnNumber;
    private Integer activePlayerSeat;
    private String activePlayerName;
    private String phase;
    private String step;
    private String text;
    private final List<CardInfo> cards = new ArrayList<>();
    private AbilityReference ability;
    private final List<PlayerTurnSnapshot> turnSnapshot = new ArrayList<>();
    private final List<CombatAttackAssignment> attackers = new ArrayList<>();
    private final List<CombatBlockAssignment> blockers = new ArrayList<>();
    /** Canonical battlefield observation consumed by BoardStateMonitor. */
    private final List<BoardPermanentSnapshot> battlefieldObservation = new ArrayList<>();
    private GameResult gameResult;
    private MatchScore matchScore;
    private MatchResult matchResult;
}
