package app.projection;

import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * Owns Room parent/facet correlation, half naming, unlocked-half snapshots,
 * and repair of cast events emitted before parent metadata was available.
 */
final class RoomProjectionSupport {
    enum Half { LEFT, RIGHT }

    private record CastProjection(GameEvent event, Half half, int seatId) {}

    private final GameState state;
    private final ObjectIdentityTracker identities;
    private final IntFunction<String> playerName;
    private final BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectName;
    private final Map<Long, List<CastProjection>> castEvents = new LinkedHashMap<>();

    RoomProjectionSupport(
            GameState state,
            ObjectIdentityTracker identities,
            IntFunction<String> playerName,
            BiFunction<GameObjectState, Map<Long, CardInfo>, String> objectName) {
        this.state = state;
        this.identities = identities;
        this.playerName = playerName;
        this.objectName = objectName;
    }

    boolean isParent(GameObjectState object) {
        return object != null && !isFacet(object)
                && object.getSubtypes().contains("Room");
    }

    boolean isFacet(GameObjectState object) {
        if (object == null) return false;
        String objectType = object.getObjectType();
        if ("GameObjectType_RoomLeft".equals(objectType)
                || "GameObjectType_RoomRight".equals(objectType)) {
            return true;
        }
        return object.getParentId() >= 0 && object.getSubtypes().contains("Room");
    }

    Half halfFor(GameObjectState room) {
        if (!isParent(room) || room.getGrpId() <= 0) return null;
        for (GameObjectState candidate : state.getObjects().values()) {
            if (!isFacet(candidate) || candidate.getGrpId() != room.getGrpId()) continue;
            long parentLogicalId = identities.logicalIdOf(candidate.getParentId());
            if (parentLogicalId != room.getLogicalObjectId()) continue;
            return halfFromObjectType(candidate.getObjectType());
        }
        return null;
    }

    String describeCast(GameObjectState room, Map<Long, CardInfo> cards, Half half) {
        return playerName.apply(room.getControllerSeatId())
                + " casts " + halfName(room, cards, half);
    }

    void rememberCast(GameObjectState room, GameEvent event, Half half, int seatId) {
        castEvents.computeIfAbsent(
                        room.getLogicalObjectId(), ignored -> new ArrayList<>())
                .add(new CastProjection(event, half, seatId));
    }

    void repairCastEvents(GameObjectState room, Map<Long, CardInfo> cards) {
        if (!isParent(room)) return;
        CardInfo parentCard = parentCard(room, cards);
        if (parentCard == null) return;
        List<CastProjection> projections = castEvents.get(room.getLogicalObjectId());
        if (projections == null) return;
        for (CastProjection projection : projections) {
            projection.event().setText(
                    playerName.apply(projection.seatId()) + " casts "
                            + halfName(room, cards, projection.half()));
            if (!projection.event().getCards().contains(parentCard)) {
                projection.event().getCards().add(parentCard);
            }
        }
    }

    List<String> unlockedHalfNames(
            GameObjectState room, Map<Long, CardInfo> cards) {
        if (!isParent(room) || room.getUnlockedRoomGrpIds().isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (long grpId : room.getUnlockedRoomGrpIds()) {
            Half half = null;
            for (GameObjectState candidate : state.getObjects().values()) {
                if (isFacet(candidate) && candidate.getGrpId() == grpId) {
                    half = halfFromObjectType(candidate.getObjectType());
                    break;
                }
            }
            String name = halfName(room, cards, half);
            if (!names.contains(name)) names.add(name);
        }
        return List.copyOf(names);
    }

    void clear() {
        castEvents.clear();
    }

    private String halfName(
            GameObjectState room, Map<Long, CardInfo> cards, Half half) {
        CardInfo parentCard = parentCard(room, cards);
        if (parentCard != null && parentCard.getCardFaces() != null
                && parentCard.getCardFaces().size() >= 2 && half != null) {
            CardFaceInfo face = parentCard.getCardFaces().get(half == Half.LEFT ? 0 : 1);
            if (face != null && face.getName() != null && !face.getName().isBlank()) {
                return face.getName();
            }
        }
        return half == Half.LEFT ? "left Room half"
                : half == Half.RIGHT ? "right Room half"
                : objectName.apply(room, cards);
    }

    private CardInfo parentCard(GameObjectState room, Map<Long, CardInfo> cards) {
        if (room == null) return null;
        if (hasTwoFaces(room.getCard())) return room.getCard();
        CardInfo direct = cards.get(room.getGrpId());
        return hasTwoFaces(direct) ? direct : null;
    }

    private boolean hasTwoFaces(CardInfo card) {
        return card != null && card.getCardFaces() != null
                && card.getCardFaces().size() >= 2;
    }

    private Half halfFromObjectType(String objectType) {
        return "GameObjectType_RoomLeft".equals(objectType)
                ? Half.LEFT
                : "GameObjectType_RoomRight".equals(objectType)
                ? Half.RIGHT
                : null;
    }
}
