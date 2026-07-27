package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.ZoneInfo;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSnapshotProjectorTest {
    @Test
    void observesLifeAndPoisonAndReportsLaterPoisonChanges() {
        GameState state = new GameState();
        state.getPlayers().put(1, "Alice");
        PlayerSnapshotProjector projector = projector(state);

        assertTrue(projector.observePlayers(JsonParser.parseString("""
                [{"systemSeatNumber":1,"lifeTotal":20,"poisonCount":1}]
                """).getAsJsonArray()).isEmpty());
        var changes = projector.observePlayers(JsonParser.parseString("""
                [{"systemSeatNumber":1,"lifeTotal":18,"poisonCount":3}]
                """).getAsJsonArray());

        assertEquals(18, state.getLifeTotals().get(1));
        assertEquals(3, state.getPoisonCounters().get(1));
        assertEquals(List.of("Alice gets 2 poison counters (3 total)"), changes);
    }

    @Test
    void buildsPlayerAndBattlefieldSnapshotsFromCanonicalState() {
        GameState state = new GameState();
        state.getPlayers().put(1, "Alice");
        state.getLifeTotals().put(1, 17);
        state.getPoisonCounters().put(1, 2);

        ZoneInfo battlefield = zone(10, 1, "Battlefield", 1);
        ZoneInfo hand = zone(20, 1, "Hand", 4);
        state.getZones().put(10, battlefield);
        state.getZones().put(20, hand);

        CardInfo card = new CardInfo();
        card.setName("Test Creature");
        card.setKeywords(List.of("Flying"));
        GameObjectState object = new GameObjectState();
        object.setInstanceId(100);
        object.setLogicalObjectId(100);
        object.setOwnerSeatId(1);
        object.setControllerSeatId(1);
        object.setSemanticZoneId(10);
        object.setCard(card);
        state.getObjects().put(100L, object);

        PlayerSnapshotProjector.TurnSnapshot snapshot =
                projector(state).snapshot(Map.of());

        assertEquals(1, snapshot.battlefield().size());
        assertEquals("Test Creature", snapshot.battlefield().get(0).getName());
        assertEquals(List.of("flying"),
                snapshot.battlefield().get(0).getEvergreenAbilities());
        assertEquals(4, snapshot.players().get(0).getHandSize());
        assertEquals(17, snapshot.players().get(0).getLifeTotal());
        assertEquals(2, snapshot.players().get(0).getPoisonCounters());
    }

    private PlayerSnapshotProjector projector(GameState state) {
        return new PlayerSnapshotProjector(new PlayerSnapshotProjector.Context() {
            @Override public GameState state() { return state; }
            @Override public boolean isCurrent(GameObjectState object) { return true; }
            @Override public String zoneType(int zoneId) {
                ZoneInfo zone = state.getZones().get(zoneId);
                return zone == null ? "" : zone.displayName();
            }
            @Override public boolean isAbility(GameObjectState object) { return false; }
            @Override public boolean isRoomFacet(GameObjectState object) { return false; }
            @Override public String objectName(
                    GameObjectState object, Map<Long, CardInfo> cards) {
                return object.getCard() == null ? "Unknown" : object.getCard().getName();
            }
            @Override public Long attachedHost(long logicalObjectId) { return null; }
            @Override public List<String> unlockedRoomHalves(
                    GameObjectState object, Map<Long, CardInfo> cards) {
                return List.of();
            }
            @Override public String playerName(int seatId) {
                return state.getPlayers().getOrDefault(seatId, "Seat " + seatId);
            }
        });
    }

    private ZoneInfo zone(int id, int owner, String type, int count) {
        ZoneInfo zone = new ZoneInfo();
        zone.setZoneId(id);
        zone.setOwnerSeatId(owner);
        zone.setType(type);
        zone.setObjectCount(count);
        return zone;
    }
}
