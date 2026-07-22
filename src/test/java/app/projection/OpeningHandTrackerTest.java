package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.ZoneInfo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies opening-hand correlation independently from GRE decoding and event wording.
 */
final class OpeningHandTrackerTest {

    private final OpeningHandTracker tracker = new OpeningHandTracker();

    @Test
    void capturesLargestVisibleKnownHand() {
        GameState state = stateWithHandZone();
        Map<Long, CardInfo> cards = knownCards(101, 102, 103);
        addHandCard(state, 1, 101, 1001);
        addHandCard(state, 1, 102, 1002);
        addHandCard(state, 2, 103, 1003);

        tracker.observe(state, cards);

        assertEquals(1, state.getOpeningHandSeat());
        assertEquals(List.of(101L, 102L), state.getOpeningHandGrpIds().get(1));
        assertEquals(0, state.getMulliganCount());
    }

    @Test
    void recordsReplacementHandAsMulligan() {
        GameState state = stateWithHandZone();
        Map<Long, CardInfo> cards = knownCards(101, 102, 103);
        addHandCard(state, 1, 101, 1001);
        addHandCard(state, 1, 102, 1002);
        tracker.observe(state, cards);

        state.getObjects().clear();
        addHandCard(state, 1, 103, 1003);
        tracker.observe(state, cards);

        assertEquals(1, state.getMulliganCount());
        assertEquals(List.of(103L), state.getOpeningHandGrpIds().get(1));
    }

    @Test
    void recordsSameSizedReplacementHandAsMulligan() {
        GameState state = stateWithHandZone();
        Map<Long, CardInfo> cards = knownCards(101, 102, 103, 104);
        addHandCard(state, 1, 101, 1001);
        addHandCard(state, 1, 102, 1002);
        tracker.observe(state, cards);

        state.getObjects().clear();
        addHandCard(state, 1, 103, 1003);
        addHandCard(state, 1, 104, 1004);
        tracker.observe(state, cards);

        assertEquals(1, state.getMulliganCount());
        assertEquals(List.of(103L, 104L), state.getOpeningHandGrpIds().get(1));
    }

    @Test
    void finalizesAtStartOfFirstTurn() {
        GameState state = stateWithHandZone();
        state.setTurnNumber(1);
        Map<Long, CardInfo> cards = knownCards(101);
        addHandCard(state, 1, 101, 1001);

        tracker.observe(state, cards);

        assertTrue(state.isOpeningHandFinalized());
        assertEquals(List.of(101L), state.getOpeningHandGrpIds().get(1));
    }

    private GameState stateWithHandZone() {
        GameState state = new GameState();
        ZoneInfo hand = new ZoneInfo();
        hand.setZoneId(10);
        hand.setType("ZoneType_Hand");
        state.getZones().put(10, hand);
        return state;
    }

    private Map<Long, CardInfo> knownCards(long... grpIds) {
        Map<Long, CardInfo> cards = new LinkedHashMap<>();
        for (long grpId : grpIds) {
            CardInfo card = new CardInfo();
            card.setArenaId(grpId);
            cards.put(grpId, card);
        }
        return cards;
    }

    private void addHandCard(GameState state, int ownerSeat, long grpId, long instanceId) {
        GameObjectState object = new GameObjectState();
        object.setInstanceId(instanceId);
        object.setLogicalObjectId(instanceId);
        object.setGrpId(grpId);
        object.setOwnerSeatId(ownerSeat);
        object.setSemanticZoneId(10);
        state.getObjects().put(instanceId, object);
    }
}
