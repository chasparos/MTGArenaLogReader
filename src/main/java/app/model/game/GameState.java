package app.model.game;

import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Data
/**
 * Represents GameState within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class GameState {
    private String matchId;
    private Integer turnNumber;
    private Integer activePlayerSeat;
    private String phase = "";
    private String step = "";
    private boolean completionEmitted;
    private final Map<Integer, String> players = new HashMap<>();
    /** Last known local Arena account name, persisted independently of a match. */
    private String localPlayerName;
    private final Map<Integer, ZoneInfo> zones = new HashMap<>();
    private final Map<Long, GameObjectState> objects = new HashMap<>();
    private final Map<Long, Long> logicalIds = new HashMap<>();
    /** Current Arena instance representing each logical object. Older aliases remain
     * addressable for annotations, but must not participate in canonical scans. */
    private final Map<Long, Long> currentInstanceByLogicalId = new HashMap<>();
    private final Set<Long> activatedAbilityInstances = new HashSet<>();
    private final Set<Long> triggeredAbilityInstances = new HashSet<>();
    private final Set<Long> emittedAnnotationIds = new HashSet<>();
    private final Map<Integer, List<Long>> openingHandGrpIds = new HashMap<>();
    private boolean openingHandFinalized;
    private int openingHandSeat = -1;
    private int mulliganCount;
    private final Map<Integer, Integer> lifeTotals = new HashMap<>();
    private final Map<Integer, Integer> poisonCounters = new HashMap<>();
    private int lastSnapshotTurn = -1;
    /** Signatures make declaration projection idempotent across repeated GRE snapshots. */
    private String emittedAttackSignature = "";
    private String emittedBlockSignature = "";

    public void reset(String newMatchId) {
        matchId = newMatchId;
        turnNumber = null;
        activePlayerSeat = null;
        phase = "";
        step = "";
        completionEmitted = false;
        players.clear();
        zones.clear();
        objects.clear();
        logicalIds.clear();
        currentInstanceByLogicalId.clear();
        activatedAbilityInstances.clear();
        triggeredAbilityInstances.clear();
        emittedAnnotationIds.clear();
        openingHandGrpIds.clear();
        openingHandFinalized = false;
        openingHandSeat = -1;
        mulliganCount = 0;
        lifeTotals.clear();
        poisonCounters.clear();
        lastSnapshotTurn = -1;
        emittedAttackSignature = "";
        emittedBlockSignature = "";
    }
}
