package app.projection;

import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.PlayerTurnDelta;
import app.model.game.PlayerTurnSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reconstructs the next supported snapshot state by applying deterministic turn deltas. */
public final class TurnStateReconstructor {

    public List<PlayerTurnSnapshot> apply(List<PlayerTurnSnapshot> before, List<PlayerTurnDelta> deltas) {
        Map<Integer, PlayerTurnSnapshot> reconstructed = new LinkedHashMap<>();
        if (before != null) {
            before.stream()
                    .sorted(Comparator.comparingInt(PlayerTurnSnapshot::getSeatId))
                    .forEach(snapshot -> reconstructed.put(snapshot.getSeatId(), copy(snapshot)));
        }
        if (deltas != null) {
            deltas.stream()
                    .sorted(Comparator.comparingInt(PlayerTurnDelta::seatId))
                    .forEach(delta -> apply(reconstructed, delta));
        }
        return List.copyOf(reconstructed.values());
    }

    private void apply(Map<Integer, PlayerTurnSnapshot> reconstructed, PlayerTurnDelta delta) {
        PlayerTurnSnapshot snapshot = reconstructed.get(delta.seatId());
        if (snapshot == null) {
            throw new IllegalArgumentException("No baseline snapshot for seat " + delta.seatId());
        }
        if (delta.lifeChange() != null) {
            snapshot.setLifeTotal(requireKnown(snapshot.getLifeTotal(), "life total", delta.seatId()) + delta.lifeChange());
        }
        if (delta.handSizeChange() != null) {
            snapshot.setHandSize(requireKnown(snapshot.getHandSize(), "hand size", delta.seatId()) + delta.handSizeChange());
        }

        removePermanents(snapshot, delta.leftBattlefield());
        delta.enteredBattlefield().forEach(permanent -> snapshot.getBattlefield().add(copy(permanent)));
        snapshot.getBattlefield().sort(Comparator.comparingLong(BoardPermanentSnapshot::getLogicalObjectId));

        removeCards(snapshot.getKnownHand(), delta.leftKnownHand(), "known hand", delta.seatId());
        snapshot.getKnownHand().addAll(delta.enteredKnownHand());
        snapshot.getKnownGraveyard().addAll(delta.enteredKnownGraveyard());
        snapshot.getKnownExile().addAll(delta.enteredKnownExile());

        for (PlayerTurnDelta.CounterDelta counterDelta : delta.counterChanges()) {
            BoardPermanentSnapshot permanent = snapshot.getBattlefield().stream()
                    .filter(candidate -> candidate.getLogicalObjectId() == counterDelta.logicalObjectId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No permanent " + counterDelta.logicalObjectId() + " for counter delta"));
            applyCounterDelta(permanent, counterDelta);
        }
    }

    private int requireKnown(Integer value, String field, int seatId) {
        if (value == null) throw new IllegalArgumentException("Unknown " + field + " for seat " + seatId);
        return value;
    }

    private void removePermanents(PlayerTurnSnapshot snapshot, List<BoardPermanentSnapshot> removed) {
        for (BoardPermanentSnapshot permanent : removed) {
            boolean found = snapshot.getBattlefield().removeIf(candidate ->
                    candidate.getLogicalObjectId() == permanent.getLogicalObjectId());
            if (!found) {
                throw new IllegalArgumentException("No permanent " + permanent.getLogicalObjectId() + " to remove");
            }
        }
    }

    private void removeCards(List<CardInfo> zone, List<CardInfo> removed, String zoneName, int seatId) {
        for (CardInfo card : removed) {
            int index = indexOf(zone, card);
            if (index < 0) {
                throw new IllegalArgumentException("No " + identity(card) + " in " + zoneName + " for seat " + seatId);
            }
            zone.remove(index);
        }
    }

    private int indexOf(List<CardInfo> cards, CardInfo expected) {
        String expectedIdentity = identity(expected);
        for (int i = 0; i < cards.size(); i++) {
            if (identity(cards.get(i)).equals(expectedIdentity)) return i;
        }
        return -1;
    }

    private void applyCounterDelta(BoardPermanentSnapshot permanent, PlayerTurnDelta.CounterDelta delta) {
        CounterState counter = permanent.getCounters().stream()
                .filter(candidate -> counterType(candidate).equals(delta.counterType()))
                .findFirst()
                .orElse(null);
        int oldCount = counter == null ? 0 : counter.getCount();
        int newCount = oldCount + delta.change();
        if (newCount < 0) {
            throw new IllegalArgumentException("Counter count cannot become negative for " + delta.logicalObjectId());
        }
        if (newCount == 0) {
            if (counter != null) permanent.getCounters().remove(counter);
            return;
        }
        if (counter == null) {
            counter = new CounterState();
            counter.setType(delta.counterType());
            permanent.getCounters().add(counter);
        }
        counter.setCount(newCount);
    }

    private String counterType(CounterState counter) {
        return counter.getType() == null || counter.getType().isBlank()
                ? "counter#" + counter.getArenaType() : counter.getType();
    }

    private PlayerTurnSnapshot copy(PlayerTurnSnapshot source) {
        PlayerTurnSnapshot copy = new PlayerTurnSnapshot();
        copy.setSeatId(source.getSeatId());
        copy.setPlayerName(source.getPlayerName());
        copy.setLifeTotal(source.getLifeTotal());
        copy.setPoisonCounters(source.getPoisonCounters());
        copy.setHandSize(source.getHandSize());
        source.getBattlefield().forEach(permanent -> copy.getBattlefield().add(copy(permanent)));
        copy.getKnownHand().addAll(source.getKnownHand());
        copy.getKnownGraveyard().addAll(source.getKnownGraveyard());
        copy.getKnownExile().addAll(source.getKnownExile());
        return copy;
    }

    private BoardPermanentSnapshot copy(BoardPermanentSnapshot source) {
        BoardPermanentSnapshot copy = new BoardPermanentSnapshot();
        copy.setLogicalObjectId(source.getLogicalObjectId());
        copy.setOwnerSeatId(source.getOwnerSeatId());
        copy.setControllerSeatId(source.getControllerSeatId());
        copy.setName(source.getName());
        copy.setCard(source.getCard());
        copy.setTapped(source.getTapped());
        copy.setPower(source.getPower());
        copy.setToughness(source.getToughness());
        copy.setSagaChapter(source.getSagaChapter());
        copy.setAttachedToLogicalObjectId(source.getAttachedToLogicalObjectId());
        source.getCounters().forEach(counter -> copy.getCounters().add(counter.copy()));
        copy.getUnlockedRoomHalves().addAll(source.getUnlockedRoomHalves());
        copy.getEvergreenAbilities().addAll(source.getEvergreenAbilities());
        return copy;
    }

    private String identity(CardInfo card) {
        if (card == null) return "?";
        return card.getArenaId() + "|" + card.getName();
    }
}
