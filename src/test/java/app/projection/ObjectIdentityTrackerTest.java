package app.projection;

import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectIdentityTrackerTest {

    @Test
    void objectIdChangePreservesLogicalIdentityAndCopiesKnownState() {
        GameState state = new GameState();
        ObjectIdentityTracker tracker = new ObjectIdentityTracker(state);

        GameObjectState original = tracker.copyForObservation(1001L);
        original.setGrpId(777L);
        state.getObjects().put(1001L, original);

        tracker.observeIdChanges(objectIdChanged(1001L, 2001L));

        GameObjectState replacement = state.getObjects().get(2001L);
        assertEquals(1001L, tracker.logicalIdOf(2001L));
        assertEquals(2001L, replacement.getInstanceId());
        assertEquals(1001L, replacement.getLogicalObjectId());
        assertEquals(777L, replacement.getGrpId());
        assertNotSame(original, replacement);
        assertTrue(tracker.isCurrent(replacement));
    }

    @Test
    void aliasLookupReturnsTheCurrentKnownInstance() {
        GameState state = new GameState();
        ObjectIdentityTracker tracker = new ObjectIdentityTracker(state);

        GameObjectState original = tracker.copyForObservation(1001L);
        state.getObjects().put(1001L, original);
        tracker.observeIdChanges(objectIdChanged(1001L, 2001L));

        state.getObjects().remove(1001L);

        assertSame(state.getObjects().get(2001L), tracker.findIncludingAliases(1001L));
    }

    @Test
    void gameStateResetPreventsIdentityLeakageBetweenMatches() {
        GameState state = new GameState();
        ObjectIdentityTracker tracker = new ObjectIdentityTracker(state);

        GameObjectState original = tracker.copyForObservation(1001L);
        state.getObjects().put(1001L, original);
        tracker.observeIdChanges(objectIdChanged(1001L, 2001L));

        state.reset("next-match");

        assertEquals(2001L, tracker.logicalIdOf(2001L));
        assertNull(tracker.findIncludingAliases(1001L));
    }

    private JsonArray objectIdChanged(long oldId, long newId) {
        JsonObject annotation = new JsonObject();

        JsonArray types = new JsonArray();
        types.add("AnnotationType_ObjectIdChanged");
        annotation.add("type", types);

        JsonArray details = new JsonArray();
        details.add(detail("orig_id", oldId));
        details.add(detail("new_id", newId));
        annotation.add("details", details);

        JsonArray annotations = new JsonArray();
        annotations.add(annotation);
        return annotations;
    }

    private JsonObject detail(String key, long value) {
        JsonObject detail = new JsonObject();
        detail.addProperty("key", key);

        JsonArray values = new JsonArray();
        values.add(value);
        detail.add("valueInt32", values);
        return detail;
    }
}
