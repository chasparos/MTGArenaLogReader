package app.coaching.analysis;

import app.model.game.PlayerTurnDelta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TurnCoachingMetricsCalculatorTest {

    @Test
    void derivesDescriptiveCountsWithoutEvaluativeLabels() {
        PlayerTurnDelta delta = new PlayerTurnDelta(
                1, "Me", -3, -1,
                List.of(new app.model.game.BoardPermanentSnapshot()),
                List.of(new app.model.game.BoardPermanentSnapshot(), new app.model.game.BoardPermanentSnapshot()),
                List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new PlayerTurnDelta.CounterDelta(10, "Bear", "+1/+1", 2),
                        new PlayerTurnDelta.CounterDelta(11, "Rat", "Shield", -1)));

        TurnCoachingMetrics metrics = new TurnCoachingMetricsCalculator().calculate(List.of(delta)).get(0);

        assertEquals(-3, metrics.lifeChange());
        assertEquals(-1, metrics.handSizeChange());
        assertEquals(1, metrics.permanentsEntered());
        assertEquals(2, metrics.permanentsLeft());
        assertEquals(2, metrics.countersAdded());
        assertEquals(1, metrics.countersRemoved());
    }
}
