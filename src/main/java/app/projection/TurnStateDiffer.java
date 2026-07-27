package app.projection;

import app.model.card.CardInfo;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.PlayerTurnDelta;
import app.model.game.PlayerTurnSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Computes deterministic, conservative changes between reliable turn snapshots. */
public final class TurnStateDiffer {

    public List<PlayerTurnDelta> diff(List<PlayerTurnSnapshot> before, List<PlayerTurnSnapshot> after) {
        Map<Integer, PlayerTurnSnapshot> previous = bySeat(before);
        List<PlayerTurnDelta> result = new ArrayList<>();
        after.stream().sorted(Comparator.comparingInt(PlayerTurnSnapshot::getSeatId)).forEach(current -> {
            PlayerTurnSnapshot prior = previous.get(current.getSeatId());
            if (prior == null) return;
            PlayerTurnDelta delta = diff(prior, current);
            if (!delta.isEmpty()) result.add(delta);
        });
        return List.copyOf(result);
    }

    private PlayerTurnDelta diff(PlayerTurnSnapshot before, PlayerTurnSnapshot after) {
        Map<Long, BoardPermanentSnapshot> oldBoard = boardById(before.getBattlefield());
        Map<Long, BoardPermanentSnapshot> newBoard = boardById(after.getBattlefield());
        List<BoardPermanentSnapshot> entered = newBoard.entrySet().stream()
                .filter(entry -> !oldBoard.containsKey(entry.getKey())).map(Map.Entry::getValue)
                .sorted(Comparator.comparingLong(BoardPermanentSnapshot::getLogicalObjectId)).toList();
        List<BoardPermanentSnapshot> left = oldBoard.entrySet().stream()
                .filter(entry -> !newBoard.containsKey(entry.getKey())).map(Map.Entry::getValue)
                .sorted(Comparator.comparingLong(BoardPermanentSnapshot::getLogicalObjectId)).toList();

        List<PlayerTurnDelta.CounterDelta> counters = new ArrayList<>();
        for (Map.Entry<Long, BoardPermanentSnapshot> entry : newBoard.entrySet()) {
            BoardPermanentSnapshot oldPermanent = oldBoard.get(entry.getKey());
            if (oldPermanent == null) continue;
            Map<String, Integer> oldCounters = counters(oldPermanent);
            Map<String, Integer> newCounters = counters(entry.getValue());
            java.util.Set<String> types = new java.util.TreeSet<>();
            types.addAll(oldCounters.keySet());
            types.addAll(newCounters.keySet());
            for (String type : types) {
                int change = newCounters.getOrDefault(type, 0) - oldCounters.getOrDefault(type, 0);
                if (change != 0) counters.add(new PlayerTurnDelta.CounterDelta(
                        entry.getKey(), entry.getValue().getName(), type, change));
            }
        }

        return new PlayerTurnDelta(after.getSeatId(), after.getPlayerName(),
                difference(before.getLifeTotal(), after.getLifeTotal()),
                difference(before.getHandSize(), after.getHandSize()),
                entered, left,
                addedCards(before.getKnownHand(), after.getKnownHand()),
                addedCards(after.getKnownHand(), before.getKnownHand()),
                addedCards(before.getKnownGraveyard(), after.getKnownGraveyard()),
                addedCards(before.getKnownExile(), after.getKnownExile()),
                counters);
    }

    private Map<Integer, PlayerTurnSnapshot> bySeat(List<PlayerTurnSnapshot> snapshots) {
        Map<Integer, PlayerTurnSnapshot> result = new HashMap<>();
        if (snapshots != null) snapshots.forEach(snapshot -> result.put(snapshot.getSeatId(), snapshot));
        return result;
    }

    private Map<Long, BoardPermanentSnapshot> boardById(List<BoardPermanentSnapshot> board) {
        Map<Long, BoardPermanentSnapshot> result = new LinkedHashMap<>();
        board.forEach(permanent -> result.put(permanent.getLogicalObjectId(), permanent));
        return result;
    }

    private Map<String, Integer> counters(BoardPermanentSnapshot permanent) {
        Map<String, Integer> result = new HashMap<>();
        for (CounterState counter : permanent.getCounters()) {
            String type = counter.getType() == null || counter.getType().isBlank()
                    ? "counter#" + counter.getArenaType() : counter.getType();
            result.put(type, counter.getCount());
        }
        return result;
    }

    private List<CardInfo> addedCards(List<CardInfo> before, List<CardInfo> after) {
        Map<String, Integer> remaining = new HashMap<>();
        before.forEach(card -> remaining.merge(identity(card), 1, Integer::sum));
        List<CardInfo> added = new ArrayList<>();
        for (CardInfo card : after) {
            String identity = identity(card);
            int count = remaining.getOrDefault(identity, 0);
            if (count > 0) remaining.put(identity, count - 1);
            else added.add(card);
        }
        added.sort(Comparator.comparing(this::identity));
        return List.copyOf(added);
    }

    private String identity(CardInfo card) {
        if (card == null) return "?";
        return String.valueOf(card.getArenaId()) + "|" + card.getName();
    }

    private Integer difference(Integer before, Integer after) {
        return before == null || after == null ? null : after - before;
    }
}
