package app.model.game;


import app.model.card.CardInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * Represents GameObjectState within the canonical per-game state and snapshot model.
 *
 * <p>Projection code creates or mutates this data from Arena observations; replay and export layers consume derived events rather than reparsing raw messages.</p>
 *
 * <p>Observed, reconstructed, and unknown information must remain distinguishable and conservative.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the canonical per-game state model used by projection before semantic events are presented.</p>
 */
public class GameObjectState {
    private long instanceId;
    private long logicalObjectId;
    private long grpId;
    /** Active face index for a multi-faced card, when reconstructed. */
    private int activeFaceIndex;
    private String objectType = "";
    private long objectSourceGrpId;
    private long parentId = -1;
    private int ownerSeatId = -1;
    private int controllerSeatId = -1;
    private int zoneId = -1;
    private final List<String> cardTypes = new ArrayList<>();
    private final List<String> subtypes = new ArrayList<>();
    private final List<String> colors = new ArrayList<>();
    private final List<Long> uniqueAbilityGrpIds = new ArrayList<>();
    /** Arena group ids of Room halves observed being cast/unlocked for this permanent. */
    private final List<Long> unlockedRoomGrpIds = new ArrayList<>();
    /** All counter types are retained in canonical state, even when only P/T is rendered. */
    private final List<CounterState> counters = new ArrayList<>();
    private Integer power;
    private Integer toughness;
    /** Arena's explicit current tapped state when supplied. */
    private Boolean tapped;

    /** Direct combat observations from Arena. */
    private String attackState = "";
    private Long attackTargetId;
    private String blockState = "";
    private final List<Long> blockedAttackerIds = new ArrayList<>();
    /** Full Scryfall metadata for card objects when available. */
    private CardInfo card;

    /** Last non-transient zone. Arena commonly routes objects through Limbo. */
    private int semanticZoneId = -1;

    public GameObjectState copy() {
        GameObjectState copy = new GameObjectState();
        copy.instanceId = instanceId;
        copy.logicalObjectId = logicalObjectId;
        copy.grpId = grpId;
        copy.activeFaceIndex = activeFaceIndex;
        copy.objectType = objectType;
        copy.objectSourceGrpId = objectSourceGrpId;
        copy.parentId = parentId;
        copy.ownerSeatId = ownerSeatId;
        copy.controllerSeatId = controllerSeatId;
        copy.zoneId = zoneId;
        copy.semanticZoneId = semanticZoneId;
        copy.cardTypes.addAll(cardTypes);
        copy.subtypes.addAll(subtypes);
        copy.colors.addAll(colors);
        copy.uniqueAbilityGrpIds.addAll(uniqueAbilityGrpIds);
        copy.unlockedRoomGrpIds.addAll(unlockedRoomGrpIds);
        for (CounterState counter : counters) copy.counters.add(counter.copy());
        copy.power = power;
        copy.toughness = toughness;
        copy.tapped = tapped;
        copy.attackState = attackState;
        copy.attackTargetId = attackTargetId;
        copy.blockState = blockState;
        copy.blockedAttackerIds.addAll(blockedAttackerIds);
        copy.card = card;
        return copy;
    }
}
