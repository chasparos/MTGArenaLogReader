package app.model.game;

import app.model.card.CardInfo;

import java.util.List;

/** Deterministic changes between two reliable turn snapshots for one player. */
public record PlayerTurnDelta(
        int seatId,
        String playerName,
        Integer lifeChange,
        Integer handSizeChange,
        List<BoardPermanentSnapshot> enteredBattlefield,
        List<BoardPermanentSnapshot> leftBattlefield,
        List<CardInfo> enteredKnownHand,
        List<CardInfo> leftKnownHand,
        List<CardInfo> enteredKnownGraveyard,
        List<CardInfo> enteredKnownExile,
        List<CounterDelta> counterChanges) {

    public PlayerTurnDelta {
        enteredBattlefield = List.copyOf(enteredBattlefield);
        leftBattlefield = List.copyOf(leftBattlefield);
        enteredKnownHand = List.copyOf(enteredKnownHand);
        leftKnownHand = List.copyOf(leftKnownHand);
        enteredKnownGraveyard = List.copyOf(enteredKnownGraveyard);
        enteredKnownExile = List.copyOf(enteredKnownExile);
        counterChanges = List.copyOf(counterChanges);
    }

    public boolean isEmpty() {
        return zero(lifeChange) && zero(handSizeChange)
                && enteredBattlefield.isEmpty() && leftBattlefield.isEmpty()
                && enteredKnownHand.isEmpty() && leftKnownHand.isEmpty()
                && enteredKnownGraveyard.isEmpty() && enteredKnownExile.isEmpty()
                && counterChanges.isEmpty();
    }

    private boolean zero(Integer value) {
        return value == null || value == 0;
    }

    public record CounterDelta(long logicalObjectId, String permanentName, String counterType, int change) {}
}
