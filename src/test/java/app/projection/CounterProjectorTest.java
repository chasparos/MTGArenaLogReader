package app.projection;

import app.model.game.CounterState;
import app.model.game.GameObjectState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CounterProjectorTest {
    private final CounterProjector projector = new CounterProjector();

    @Test
    void appliesAndRemovesCounterDeltas() {
        GameObjectState object = new GameObjectState();

        projector.applyDelta(object, 1, 2);
        assertEquals(1, object.getCounters().size());
        assertEquals(2, object.getCounters().get(0).getCount());
        assertEquals("+1/+1", object.getCounters().get(0).getType());

        projector.applyDelta(object, 1, -2);
        assertTrue(object.getCounters().isEmpty());
    }

    @Test
    void absoluteCountsReuseTheExistingArenaCounter() {
        GameObjectState object = new GameObjectState();
        CounterState existing = new CounterState();
        existing.setArenaType(2);
        existing.setType("stale");
        existing.setCount(1);
        object.getCounters().add(existing);

        projector.setCount(object, 2, 4);

        assertSame(existing, object.getCounters().get(0));
        assertEquals(4, existing.getCount());
        assertEquals("-1/-1", existing.getType());
    }

    @Test
    void namesUnknownCounterTypesDeterministically() {
        assertEquals("Poison", projector.counterTypeName(3));
        assertEquals("Counter#42", projector.counterTypeName(42));
    }
}
