package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectNameResolverTest {
    @Test
    void usesEnrichedNamesAndConservativeObservedFallbacks() {
        Fixture fixture = fixture();
        GameObjectState object = object(10, 500);
        object.getColors().add("Blue");
        object.getSubtypes().add("Wizard");
        object.getCardTypes().add("Creature");
        object.setPower(2);
        object.setToughness(3);

        assertEquals("Unknown blue Wizard creature (2/3) [Arena #500]",
                fixture.names.displayName(object, Map.of()));

        CardInfo card = new CardInfo();
        card.setName("Helpful Wizard");
        assertEquals("Helpful Wizard",
                fixture.names.displayName(object, Map.of(500L, card)));
    }

    @Test
    void retainsSourceNamesAfterTransientObjectsDisappear() {
        Fixture fixture = fixture();
        GameObjectState source = object(10, 500);
        CardInfo card = new CardInfo();
        card.setName("Source Card");
        fixture.state.getObjects().put(10L, source);
        fixture.names.remember(10, source, Map.of(500L, card));
        fixture.state.getObjects().clear();

        assertEquals("Source Card",
                fixture.names.sourceName(10, -1, Map.of()));
    }

    @Test
    void repairsPlaceholderTextWhenCardMetadataArrives() {
        Fixture fixture = fixture();
        GameEvent event = new GameEvent();
        event.setText("Alice casts ArenaCard#500");
        fixture.events.add(event);
        CardInfo card = new CardInfo();
        card.setName("Known Spell");

        fixture.names.repairPreviouslyUnknownNames(Map.of(500L, card));

        assertEquals("Alice casts Known Spell", event.getText());
    }

    private Fixture fixture() {
        GameState state = new GameState();
        Map<Long, GameObjectState> observed = new LinkedHashMap<>();
        List<GameEvent> events = new ArrayList<>();
        ObjectNameResolver names = new ObjectNameResolver(
                state,
                new ObjectIdentityTracker(state),
                new AbilityNameStore(),
                new TokenResolver(),
                observed,
                events,
                seat -> "Seat " + seat);
        return new Fixture(state, events, names);
    }

    private GameObjectState object(long instanceId, long grpId) {
        GameObjectState object = new GameObjectState();
        object.setInstanceId(instanceId);
        object.setLogicalObjectId(instanceId);
        object.setGrpId(grpId);
        return object;
    }

    private record Fixture(
            GameState state, List<GameEvent> events, ObjectNameResolver names) {}
}
