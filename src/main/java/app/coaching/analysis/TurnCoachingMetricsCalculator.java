package app.coaching.analysis;

import app.model.game.PlayerTurnDelta;

import java.util.Comparator;
import java.util.List;

/** Converts structured turn deltas into deterministic coaching-ready facts. */
public final class TurnCoachingMetricsCalculator {

    public List<TurnCoachingMetrics> calculate(List<PlayerTurnDelta> deltas) {
        if (deltas == null || deltas.isEmpty()) return List.of();
        return deltas.stream()
                .sorted(Comparator.comparingInt(PlayerTurnDelta::seatId))
                .map(this::calculate)
                .toList();
    }

    private TurnCoachingMetrics calculate(PlayerTurnDelta delta) {
        int countersAdded = delta.counterChanges().stream()
                .mapToInt(change -> Math.max(change.change(), 0))
                .sum();
        int countersRemoved = delta.counterChanges().stream()
                .mapToInt(change -> Math.max(-change.change(), 0))
                .sum();
        return new TurnCoachingMetrics(
                delta.seatId(),
                delta.playerName(),
                delta.lifeChange(),
                delta.handSizeChange(),
                delta.enteredBattlefield().size(),
                delta.leftBattlefield().size(),
                delta.enteredKnownHand().size(),
                delta.leftKnownHand().size(),
                delta.enteredKnownGraveyard().size(),
                delta.enteredKnownExile().size(),
                countersAdded,
                countersRemoved);
    }
}
