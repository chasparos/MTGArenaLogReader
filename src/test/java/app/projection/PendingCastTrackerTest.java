package app.projection;

import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingCastTrackerTest {
    @Test
    void recognizesCancelActionsOnlyFromCancelValues() {
        assertTrue(PendingCastTracker.containsCancelAction(
                JsonParser.parseString("""
                        {"response":{"actions":["SelectAction_Cancel_1"]}}
                        """)));
        assertFalse(PendingCastTracker.containsCancelAction(
                JsonParser.parseString("""
                        {"label":"Cancel button settings"}
                        """)));
    }

    @Test
    void removesTheMostRecentlyRememberedCast() {
        PendingCastTracker tracker = tracker(new GameState());
        tracker.remember(10, 100, 1, "First");
        tracker.remember(20, 200, 2, "Second");

        assertEquals("Second", tracker.removeMostRecent().name());
        assertEquals("First", tracker.removeMostRecent().name());
        assertNull(tracker.removeMostRecent());
    }

    @Test
    void correlatesReplacementInstancesByLogicalIdentity() {
        GameState state = new GameState();
        state.getLogicalIds().put(10L, 10L);
        state.getLogicalIds().put(11L, 10L);
        PendingCastTracker tracker = tracker(state);
        tracker.remember(10, 100, 1, "Spell");

        GameObjectState replacement = new GameObjectState();
        replacement.setInstanceId(11);
        replacement.setGrpId(100);

        assertEquals("Spell", tracker.removeFor(11, replacement).name());
        assertNull(tracker.removeMostRecent());
    }

    private PendingCastTracker tracker(GameState state) {
        return new PendingCastTracker(new ObjectIdentityTracker(state));
    }
}
