package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameObjectProjectorTest {
    @Test
    void appliesIdentityCardCharacteristicsCountersAndCombatState() {
        GameState state = new GameState();
        CardInfo card = new CardInfo();
        card.setName("Test Creature");
        GameObjectProjector.Observation observation = projector(state).apply(
                JsonParser.parseString("""
                        {
                          "instanceId":100,
                          "grpId":42,
                          "type":"GameObjectType_Card",
                          "ownerSeatId":1,
                          "controllerSeatId":2,
                          "zoneId":10,
                          "cardTypes":["CardType_Creature"],
                          "subtypes":["SubType_Wizard"],
                          "color":["CardColor_Blue"],
                          "uniqueAbilities":[{"grpId":700}],
                          "counters":[{"counterTypeId":1,"count":2}],
                          "power":{"value":3},
                          "toughness":{"value":4},
                          "isTapped":true,
                          "attackState":"AttackState_Attacking",
                          "attackInfo":{"targetId":2}
                        }
                        """).getAsJsonObject(),
                Map.of(42L, card));

        GameObjectState current = observation.current();
        assertEquals(card, current.getCard());
        assertEquals(10, current.getSemanticZoneId());
        assertEquals(java.util.List.of("Creature"), current.getCardTypes());
        assertEquals("+1/+1", current.getCounters().get(0).getType());
        assertEquals(2, current.getCounters().get(0).getCount());
        assertTrue(current.getTapped());
        assertEquals(2L, current.getAttackTargetId());
    }

    @Test
    void treatsAbsentTappedFlagAsUntappedInNextFullSnapshot() {
        GameState state = new GameState();
        GameObjectProjector projector = projector(state);
        projector.apply(JsonParser.parseString("""
                {"instanceId":100,"zoneId":10,"isTapped":true}
                """).getAsJsonObject(), Map.of());

        GameObjectProjector.Observation observation = projector.apply(
                JsonParser.parseString("""
                        {"instanceId":100,"zoneId":10}
                        """).getAsJsonObject(), Map.of());

        assertTrue(observation.previous().getTapped());
        assertFalse(observation.current().getTapped());
    }

    @Test
    void retainsLastSemanticZoneWhileObjectIsTransient() {
        GameState state = new GameState();
        GameObjectProjector projector = projector(state);
        projector.apply(JsonParser.parseString("""
                {"instanceId":100,"zoneId":10}
                """).getAsJsonObject(), Map.of());

        GameObjectProjector.Observation observation = projector.apply(
                JsonParser.parseString("""
                        {"instanceId":100,"zoneId":99}
                        """).getAsJsonObject(), Map.of());

        assertEquals(99, observation.incomingZone());
        assertEquals(10, observation.current().getSemanticZoneId());
    }

    @Test
    void ignoresObservationWithoutInstanceIdentity() {
        assertNull(projector(new GameState()).apply(
                JsonParser.parseString("{}").getAsJsonObject(), Map.of()));
    }

    private static GameObjectProjector projector(GameState state) {
        return new GameObjectProjector(
                state,
                new ObjectIdentityTracker(state),
                new CounterProjector(),
                new TokenResolver(),
                Map.of(),
                zone -> zone == 99);
    }
}
