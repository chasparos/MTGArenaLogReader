package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.log.LogMessageInterface;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static app.projection.ArenaJson.detailLong;
import static app.projection.ArenaJson.detailString;
import static app.projection.ArenaJson.hasType;
import static app.projection.ArenaJson.longArray;

/** Projects authoritative Arena zone-transfer annotations and their correlations. */
final class ZoneTransferProjector {
    interface Context {
        String zoneType(int zoneId);
        PendingCastTracker.PendingCast removePendingCast(
                long instanceId, GameObjectState object);
        GameEvent cancelledCast(
                LogMessageInterface source, PendingCastTracker.PendingCast pending);
        GameEvent objectEvent(
                LogMessageInterface source, String text, GameObjectState object);
        String transition(
                GameObjectState before, GameObjectState object,
                Map<Long, CardInfo> cards, String category);
    }

    private final GameState state;
    private final RoomProjectionSupport rooms;
    private final Predicate<JsonObject> markAnnotation;
    private final Context context;

    ZoneTransferProjector(GameState state,
                          RoomProjectionSupport rooms,
                          Predicate<JsonObject> markAnnotation,
                          Context context) {
        this.state = state;
        this.rooms = rooms;
        this.markAnnotation = markAnnotation;
        this.context = context;
    }

    void project(LogMessageInterface source, JsonArray annotations,
                 Map<Long, CardInfo> cards, List<GameEvent> result) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;
            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, "AnnotationType_ZoneTransfer")
                    || !markAnnotation.test(annotation)) {
                continue;
            }
            int fromZone = (int) detailLong(annotation, "zone_src", -1);
            int toZone = (int) detailLong(annotation, "zone_dest", -1);
            String category = detailString(annotation, "category");
            for (long instanceId : longArray(annotation, "affectedIds")) {
                projectObject(source, cards, result, instanceId,
                        fromZone, toZone, category);
            }
        }
    }

    private void projectObject(LogMessageInterface source,
                               Map<Long, CardInfo> cards,
                               List<GameEvent> result,
                               long instanceId,
                               int fromZone,
                               int toZone,
                               String category) {
        GameObjectState object = state.getObjects().get(instanceId);
        if (object == null || rooms.isFacet(object)) return;

        GameObjectState before = sourceFaceBeforeTransfer(object, instanceId, fromZone);
        before.setSemanticZoneId(fromZone);
        object.setSemanticZoneId(toZone);
        object.setZoneId(toZone);

        PendingCastTracker.PendingCast cancellation =
                correlatePendingCast(instanceId, object, fromZone, toZone);
        if (cancellation != null) {
            result.add(context.cancelledCast(source, cancellation));
            return;
        }

        if ("CastSpell".equals(category) && rooms.isParent(object)) {
            projectRoomCast(source, cards, result, object);
            return;
        }
        result.add(context.objectEvent(
                source,
                context.transition(before, object, cards, category),
                object));
    }

    private GameObjectState sourceFaceBeforeTransfer(
            GameObjectState object, long instanceId, int fromZone) {
        return state.getObjects().values().stream()
                .filter(candidate -> candidate.getInstanceId() != instanceId)
                .filter(candidate -> candidate.getLogicalObjectId() == object.getLogicalObjectId())
                .filter(candidate -> candidate.getSemanticZoneId() == fromZone)
                .filter(candidate -> candidate.getGrpId() != object.getGrpId())
                .findFirst()
                .map(GameObjectState::copy)
                .orElseGet(object::copy);
    }

    private PendingCastTracker.PendingCast correlatePendingCast(
            long instanceId, GameObjectState object, int fromZone, int toZone) {
        String from = context.zoneType(fromZone);
        String to = context.zoneType(toZone);
        if ("Hand".equals(to)
                && ("Limbo".equals(from) || "Stack".equals(from))) {
            return context.removePendingCast(instanceId, object);
        }
        if ("Stack".equals(from)) {
            context.removePendingCast(instanceId, object);
        }
        return null;
    }

    private void projectRoomCast(LogMessageInterface source,
                                 Map<Long, CardInfo> cards,
                                 List<GameEvent> result,
                                 GameObjectState object) {
        RoomProjectionSupport.Half half = rooms.halfFor(object);
        if (half != null && object.getGrpId() > 0
                && !object.getUnlockedRoomGrpIds().contains(object.getGrpId())) {
            object.getUnlockedRoomGrpIds().add(object.getGrpId());
        }
        GameEvent event = context.objectEvent(
                source, rooms.describeCast(object, cards, half), object);
        result.add(event);
        if (half != null) {
            rooms.rememberCast(object, event, half, object.getControllerSeatId());
        }
    }
}
