package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectLifecycleEventsTest {
    @Test
    void describesNewSpellAbilityLandAndPermanent() {
        ObjectLifecycleEvents events = events();

        ObjectLifecycleEvents.Description spell =
                events.newlyVisible(object(1, 20, "Card", false), Map.of());
        ObjectLifecycleEvents.Description ability =
                events.newlyVisible(object(2, 20, "Ability", false), Map.of());
        ObjectLifecycleEvents.Description land =
                events.newlyVisible(object(3, 30, "Land", true), Map.of());
        ObjectLifecycleEvents.Description permanent =
                events.newlyVisible(object(4, 30, "Card", false), Map.of());

        assertEquals("Alice casts Object 1", spell.text());
        assertFalse(spell.ability());
        assertEquals("Alice triggers ability of Object 2", ability.text());
        assertTrue(ability.ability());
        assertEquals("Alice plays Object 3 tapped", land.text());
        assertEquals(
                "Object 4 entered the battlefield untapped under Alice's control",
                permanent.text());
    }

    @Test
    void ignoresNewObjectsOutsideVisibleSemanticZones() {
        assertNull(events().newlyVisible(
                object(1, 10, "Card", false), Map.of()));
    }

    @Test
    void classifiesAndDescribesSemanticZoneTransition() {
        GameObjectState previous = object(1, 30, "Card", false);
        GameObjectState current = object(1, 40, "Card", false);

        ObjectLifecycleEvents.Transition transition =
                events().transition(previous, current, Map.of(), "");

        assertEquals("Object 1 is put into the graveyard", transition.text());
        assertEquals("Battlefield", transition.fromZone());
        assertEquals("Graveyard", transition.toZone());
        assertEquals(ZoneTransitionReason.PUT_INTO_GRAVEYARD, transition.reason());
    }

    private static ObjectLifecycleEvents events() {
        return new ObjectLifecycleEvents(new ObjectLifecycleEvents.Context() {
            @Override public String zoneType(int zoneId) {
                return switch (zoneId) {
                    case 10 -> "Hand";
                    case 20 -> "Stack";
                    case 30 -> "Battlefield";
                    case 40 -> "Graveyard";
                    default -> "Unknown";
                };
            }
            @Override public String playerName(int seatId) { return "Alice"; }
            @Override public String objectName(
                    GameObjectState object, Map<Long, CardInfo> cards) {
                return "Object " + object.getInstanceId();
            }
            @Override public boolean isAbility(GameObjectState object) {
                return "Ability".equals(object.getObjectType());
            }
            @Override public boolean isLand(
                    GameObjectState object, Map<Long, CardInfo> cards) {
                return "Land".equals(object.getObjectType());
            }
            @Override public String abilityVerb(GameObjectState ability) {
                return "triggers ability of";
            }
        });
    }

    private static GameObjectState object(
            long id, int zone, String type, boolean tapped) {
        GameObjectState object = new GameObjectState();
        object.setInstanceId(id);
        object.setSemanticZoneId(zone);
        object.setControllerSeatId(1);
        object.setObjectType(type);
        object.setTapped(tapped);
        return object;
    }
}
