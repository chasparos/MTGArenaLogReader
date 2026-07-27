package app.projection;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;
import app.model.game.GameState;
import app.model.game.ZoneInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Tracks the best Arena-observed candidate for the local player's opening hand.
 *
 * <p>This collaborator belongs inside the projection layer. It observes canonical
 * objects and dynamically learned zones after each state update, records replacement
 * hands during mulligans, and finalizes the opening hand when normal turn processing
 * begins.</p>
 *
 * <p>It does not decode GRE records, resolve card identities, emit presentation
 * events, or infer hidden cards. Only visible hand objects with known card identities
 * participate.</p>
 */
final class OpeningHandTracker {

    OptionalInt observe(GameState state, Map<Long, CardInfo> knownCards) {
        if (state.isOpeningHandFinalized()) {
            return OptionalInt.empty();
        }
        if (state.getTurnNumber() != null && state.getTurnNumber() > 1) {
            state.setOpeningHandFinalized(true);
            return OptionalInt.empty();
        }

        Map<Integer, List<Long>> visibleByOwner = visibleKnownHands(state, knownCards);
        if (visibleByOwner.isEmpty()) {
            return OptionalInt.empty();
        }

        Map.Entry<Integer, List<Long>> bestCandidate = visibleByOwner.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElse(null);
        if (bestCandidate == null) {
            return OptionalInt.empty();
        }

        List<Long> previous = state.getOpeningHandGrpIds().get(bestCandidate.getKey());
        if (previous != null
                && !previous.isEmpty()
                && !sameCards(previous, bestCandidate.getValue())) {
            state.setMulliganCount(state.getMulliganCount() + 1);
        }

        state.setOpeningHandSeat(bestCandidate.getKey());
        state.getOpeningHandGrpIds().put(
                bestCandidate.getKey(),
                new ArrayList<>(bestCandidate.getValue()));

        if (state.getTurnNumber() != null && state.getTurnNumber() >= 1) {
            state.setOpeningHandFinalized(true);
        }
        return OptionalInt.of(bestCandidate.getKey());
    }

    private Map<Integer, List<Long>> visibleKnownHands(
            GameState state,
            Map<Long, CardInfo> knownCards) {
        Map<Integer, List<Long>> visible = new LinkedHashMap<>();
        for (GameObjectState object : state.getObjects().values()) {
            if (!isHandZone(state, object.getSemanticZoneId())) {
                continue;
            }
            if (object.getGrpId() <= 0 || !knownCards.containsKey(object.getGrpId())) {
                continue;
            }
            visible.computeIfAbsent(object.getOwnerSeatId(), ignored -> new ArrayList<>())
                    .add(object.getGrpId());
        }
        return visible;
    }

    private boolean sameCards(List<Long> left, List<Long> right) {
        List<Long> sortedLeft = new ArrayList<>(left);
        List<Long> sortedRight = new ArrayList<>(right);
        sortedLeft.sort(Long::compareTo);
        sortedRight.sort(Long::compareTo);
        return sortedLeft.equals(sortedRight);
    }

    private boolean isHandZone(GameState state, int zoneId) {
        ZoneInfo zone = state.getZones().get(zoneId);
        if (zone == null || zone.getType() == null) {
            return false;
        }
        return "Hand".equals(zone.displayName());
    }
}
