package app.projection;

import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomProjectionSupportTest {
    @Test
    void correlatesFacetsAndNamesRoomCastsFromCardFaces() {
        Fixture fixture = fixture();
        assertTrue(fixture.rooms.isParent(fixture.parent));
        assertTrue(fixture.rooms.isFacet(fixture.left));
        assertFalse(fixture.rooms.isFacet(fixture.parent));
        assertEquals(RoomProjectionSupport.Half.LEFT,
                fixture.rooms.halfFor(fixture.parent));
        assertEquals("Alice casts Restricted Office",
                fixture.rooms.describeCast(
                        fixture.parent, Map.of(500L, fixture.card),
                        RoomProjectionSupport.Half.LEFT));
    }

    @Test
    void repairsEarlierFallbackCastAndExposesUnlockedHalfNames() {
        Fixture fixture = fixture();
        GameEvent event = new GameEvent();
        event.setText("Alice casts left Room half");
        fixture.rooms.rememberCast(
                fixture.parent, event, RoomProjectionSupport.Half.LEFT, 1);
        fixture.parent.getUnlockedRoomGrpIds().add(500L);

        fixture.rooms.repairCastEvents(
                fixture.parent, Map.of(500L, fixture.card));

        assertEquals("Alice casts Restricted Office", event.getText());
        assertEquals(List.of(fixture.card), event.getCards());
        assertEquals(List.of("Restricted Office"),
                fixture.rooms.unlockedHalfNames(
                        fixture.parent, Map.of(500L, fixture.card)));
    }

    private Fixture fixture() {
        GameState state = new GameState();
        GameObjectState parent = new GameObjectState();
        parent.setInstanceId(100);
        parent.setLogicalObjectId(100);
        parent.setGrpId(500);
        parent.setControllerSeatId(1);
        parent.getSubtypes().add("Room");

        GameObjectState left = new GameObjectState();
        left.setInstanceId(101);
        left.setLogicalObjectId(101);
        left.setParentId(100);
        left.setGrpId(500);
        left.setObjectType("GameObjectType_RoomLeft");
        left.getSubtypes().add("Room");
        state.getObjects().put(100L, parent);
        state.getObjects().put(101L, left);

        CardFaceInfo leftFace = new CardFaceInfo();
        leftFace.setName("Restricted Office");
        CardFaceInfo rightFace = new CardFaceInfo();
        rightFace.setName("Archive Trap");
        CardInfo card = new CardInfo();
        card.setName("Restricted Office // Archive Trap");
        card.setCardFaces(List.of(leftFace, rightFace));
        parent.setCard(card);

        ObjectIdentityTracker identities = new ObjectIdentityTracker(state);
        RoomProjectionSupport rooms = new RoomProjectionSupport(
                state, identities, seat -> "Alice",
                (object, cards) -> object.getCard().getName());
        return new Fixture(rooms, parent, left, card);
    }

    private record Fixture(
            RoomProjectionSupport rooms,
            GameObjectState parent,
            GameObjectState left,
            CardInfo card) {}
}
