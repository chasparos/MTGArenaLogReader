package app.projection;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.PlayerLifeChange;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageProjectorTest {

    @Test
    void correlatesPlayerDamageWithObservedLifeTotal() {
        GameState state = stateWithPlayers();
        state.getLifeTotals().put(1, 17);
        GameObjectState source = permanent(9001, "Lightning Bolt", "Instant");
        state.getObjects().put(source.getInstanceId(), source);

        List<GameEvent> events = new ArrayList<>();
        projector(state).project(
                null,
                Map.of(1, 20, 2, 20),
                annotations(damage(101, 9001, 1, 3)),
                Map.of(),
                events);

        assertEquals(1, events.size());
        GameEvent event = events.getFirst();
        assertEquals(GameEventType.PLAYER_LIFE_CHANGE, event.getType());
        assertEquals("Alice takes 3 damage from Lightning Bolt (17 life)", event.getText());
        assertEquals(PlayerLifeChange.Kind.DAMAGE, event.getPlayerLifeChange().kind());
        assertEquals(20, event.getPlayerLifeChange().previousLife());
        assertEquals(17, event.getPlayerLifeChange().currentLife());
    }

    @Test
    void preservesLifeGainWithoutDamageAnnotation() {
        GameState state = stateWithPlayers();
        state.getLifeTotals().put(1, 23);

        List<GameEvent> events = new ArrayList<>();
        projector(state).project(
                null,
                Map.of(1, 20, 2, 20),
                new JsonArray(),
                Map.of(),
                events);

        assertEquals(1, events.size());
        assertEquals("Alice gains 3 life (23 life)", events.getFirst().getText());
        assertEquals(PlayerLifeChange.Kind.LIFE_GAIN,
                events.getFirst().getPlayerLifeChange().kind());
    }


    @Test
    void preservesDamageAndLifeGainWhenTheNetLifeTotalIsUnchanged() {
        GameState state = stateWithPlayers();
        GameObjectState source = permanent(9001, "Lifelink Creature", "Creature");
        state.getObjects().put(source.getInstanceId(), source);

        List<GameEvent> events = new ArrayList<>();
        projector(state).project(
                null,
                Map.of(1, 20, 2, 20),
                annotations(damage(104, 9001, 1, 3)),
                Map.of(),
                events);

        assertEquals(2, events.size());
        assertEquals("Alice takes 3 damage from Lifelink Creature (17 life)",
                events.get(0).getText());
        assertEquals("Alice gains 3 life (20 life)", events.get(1).getText());
    }

    @Test
    void emitsPlaneswalkerDamageWithSourceAndTarget() {
        GameState state = stateWithPlayers();
        GameObjectState source = permanent(9001, "Lightning Strike", "Instant");
        GameObjectState target = permanent(7001, "Teferi", "Planeswalker");
        state.getObjects().put(source.getInstanceId(), source);
        state.getObjects().put(target.getInstanceId(), target);

        List<GameEvent> events = new ArrayList<>();
        projector(state).project(
                null,
                Map.of(1, 20, 2, 20),
                annotations(damage(102, 9001, 7001, 3)),
                Map.of(),
                events);

        assertEquals(1, events.size());
        GameEvent event = events.getFirst();
        assertEquals(GameEventType.PLANESWALKER_DAMAGE, event.getType());
        assertEquals("Lightning Strike deals 3 damage to Teferi", event.getText());
        assertEquals(7001L, event.getPermanentDamage().targetLogicalObjectId());
        assertEquals("Lightning Strike", event.getPermanentDamage().sourceName());
    }

    @Test
    void ignoresNonlethalCreatureDamageAndRepeatedAnnotations() {
        GameState state = stateWithPlayers();
        GameObjectState source = permanent(9001, "Shock", "Instant");
        GameObjectState creature = permanent(8001, "Bear", "Creature");
        state.getObjects().put(source.getInstanceId(), source);
        state.getObjects().put(creature.getInstanceId(), creature);

        DamageProjector projector = projector(state);
        JsonArray annotations = annotations(damage(103, 9001, 8001, 2));
        List<GameEvent> events = new ArrayList<>();

        projector.project(null, Map.of(1, 20, 2, 20), annotations, Map.of(), events);
        projector.project(null, Map.of(1, 20, 2, 20), annotations, Map.of(), events);

        assertTrue(events.isEmpty());
    }

    private static DamageProjector projector(GameState state) {
        ObjectIdentityTracker identities = new ObjectIdentityTracker(state);
        return new DamageProjector(
                state,
                identities,
                seat -> state.getPlayers().get(seat),
                (object, cards) -> object.getCard().getName(),
                (source, text) -> {
                    GameEvent event = new GameEvent();
                    event.setText(text);
                    return event;
                },
                annotation -> {
                    long id = annotation.get("id").getAsLong();
                    return state.getEmittedAnnotationIds().add(id);
                });
    }

    private static GameState stateWithPlayers() {
        GameState state = new GameState();
        state.getPlayers().put(1, "Alice");
        state.getPlayers().put(2, "Bob");
        state.getLifeTotals().put(1, 20);
        state.getLifeTotals().put(2, 20);
        return state;
    }

    private static GameObjectState permanent(long id, String name, String cardType) {
        GameObjectState object = new GameObjectState();
        object.setInstanceId(id);
        object.setLogicalObjectId(id);
        object.getCardTypes().add(cardType);
        CardInfo card = new CardInfo();
        card.setName(name);
        object.setCard(card);
        return object;
    }

    private static JsonArray annotations(String... annotations) {
        return JsonParser.parseString("[" + String.join(",", annotations) + "]").getAsJsonArray();
    }

    private static String damage(long id, long sourceId, long targetId, int amount) {
        return """
                {
                  "id":%d,
                  "affectorId":%d,
                  "affectedIds":[%d],
                  "type":["AnnotationType_DamageDealt"],
                  "details":[
                    {"key":"damage","valueInt32":[%d]}
                  ]
                }
                """.formatted(id, sourceId, targetId, amount);
    }
}
