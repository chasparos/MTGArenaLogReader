package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTransferProjectorTest {
    @Test
    void mutatesCanonicalZoneAndEmitsTransitionEvent() {
        GameState state = stateWithObject();
        List<GameEvent> result = new ArrayList<>();
        ZoneTransferProjector projector = projector(state, false, ignored -> true);

        projector.project(null, transfer(10, 30, "Resolve"), Map.of(), result);

        assertEquals(30, state.getObjects().get(100L).getZoneId());
        assertEquals(30, state.getObjects().get(100L).getSemanticZoneId());
        assertEquals("transition 10->30 Resolve", result.get(0).getText());
    }

    @Test
    void convertsStackOrLimboRollbackToPendingCastCancellation() {
        GameState state = stateWithObject();
        List<GameEvent> result = new ArrayList<>();
        ZoneTransferProjector projector = projector(state, true, ignored -> true);

        projector.project(null, transfer(20, 10, ""), Map.of(), result);

        assertEquals(1, result.size());
        assertEquals("cancelled Test Card", result.get(0).getText());
    }

    @Test
    void ignoresAlreadyMarkedAnnotation() {
        GameState state = stateWithObject();
        List<GameEvent> result = new ArrayList<>();
        AtomicBoolean invoked = new AtomicBoolean();
        ZoneTransferProjector projector = projector(
                state, false, ignored -> {
                    invoked.set(true);
                    return false;
                });

        projector.project(null, transfer(10, 30, ""), Map.of(), result);

        assertTrue(invoked.get());
        assertTrue(result.isEmpty());
        assertEquals(10, state.getObjects().get(100L).getSemanticZoneId());
    }

    private static ZoneTransferProjector projector(
            GameState state,
            boolean cancellation,
            java.util.function.Predicate<com.google.gson.JsonObject> marker) {
        ObjectIdentityTracker identities = new ObjectIdentityTracker(state);
        RoomProjectionSupport rooms = new RoomProjectionSupport(
                state, identities, seat -> "Alice", (object, cards) -> "Test Card");
        return new ZoneTransferProjector(
                state,
                rooms,
                marker,
                new ZoneTransferProjector.Context() {
                    @Override public String zoneType(int zoneId) {
                        return switch (zoneId) {
                            case 10 -> "Hand";
                            case 20 -> "Stack";
                            case 30 -> "Battlefield";
                            default -> "Unknown";
                        };
                    }
                    @Override public PendingCastTracker.PendingCast removePendingCast(
                            long instanceId, GameObjectState object) {
                        return cancellation
                                ? new PendingCastTracker.PendingCast(
                                        instanceId, 42, 1, "Test Card")
                                : null;
                    }
                    @Override public GameEvent cancelledCast(
                            app.model.log.LogMessageInterface source,
                            PendingCastTracker.PendingCast pending) {
                        return event("cancelled " + pending.name());
                    }
                    @Override public GameEvent objectEvent(
                            app.model.log.LogMessageInterface source,
                            String text, GameObjectState object) {
                        return event(text);
                    }
                    @Override public GameEvent transitionEvent(
                            app.model.log.LogMessageInterface source,
                            GameObjectState before, GameObjectState object,
                            Map<Long, CardInfo> cards, String category) {
                        return event("transition " + before.getSemanticZoneId()
                                + "->" + object.getSemanticZoneId() + " " + category);
                    }
                });
    }

    private static GameState stateWithObject() {
        GameState state = new GameState();
        GameObjectState object = new GameObjectState();
        object.setInstanceId(100);
        object.setLogicalObjectId(100);
        object.setGrpId(42);
        object.setSemanticZoneId(10);
        object.setZoneId(10);
        object.setControllerSeatId(1);
        state.getObjects().put(100L, object);
        return state;
    }

    private static JsonArray transfer(int from, int to, String category) {
        return JsonParser.parseString("""
                [{
                  "type":"AnnotationType_ZoneTransfer",
                  "affectedIds":[100],
                  "details":[
                    {"key":"zone_src","valueInt32":[%d]},
                    {"key":"zone_dest","valueInt32":[%d]},
                    {"key":"category","valueString":["%s"]}
                  ]
                }]
                """.formatted(from, to, category)).getAsJsonArray();
    }

    private static GameEvent event(String text) {
        GameEvent event = new GameEvent();
        event.setText(text);
        return event;
    }
}
