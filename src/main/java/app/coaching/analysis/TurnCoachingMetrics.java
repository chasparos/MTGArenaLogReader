package app.coaching.analysis;

/**
 * Deterministic per-player turn facts derived from canonical state changes.
 *
 * <p>These values are descriptive only. They deliberately do not assign
 * evaluative labels such as tempo, advantage, efficiency, or quality.</p>
 */
public record TurnCoachingMetrics(
        int seatId,
        String playerName,
        Integer lifeChange,
        Integer handSizeChange,
        int permanentsEntered,
        int permanentsLeft,
        int knownCardsEnteredHand,
        int knownCardsLeftHand,
        int knownCardsEnteredGraveyard,
        int knownCardsEnteredExile,
        int countersAdded,
        int countersRemoved) {
}
