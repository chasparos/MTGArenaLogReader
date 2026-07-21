package app.model.match;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical reconstructed knowledge whose lifetime spans every game in one Arena match.
 *
 * <p>This state deliberately excludes game-local zones, object instances, aliases,
 * combat, and turn state. New games may use these snapshots as seeds, but later
 * Arena observations remain authoritative.</p>
 */
public final class MatchState {
    private final String matchId;
    private final Map<Integer, String> players = new LinkedHashMap<>();
    private final Map<Long, CardInfo> knownCards = new LinkedHashMap<>();
    private final Map<Long, GameObjectState> observedCardsByGrpId = new LinkedHashMap<>();

    public MatchState(String matchId) {
        this.matchId = matchId;
    }

    public String getMatchId() {
        return matchId;
    }

    public synchronized Map<Integer, String> playerSnapshot() {
        return Map.copyOf(players);
    }

    public synchronized void observePlayers(Map<Integer, String> observations) {
        observations.forEach((seat, name) -> {
            if (seat != null && seat >= 0 && name != null && !name.isBlank()) {
                players.put(seat, name);
            }
        });
    }

    public synchronized Map<Long, CardInfo> knownCardSnapshot() {
        return Map.copyOf(knownCards);
    }

    public synchronized void observeKnownCards(Map<Long, CardInfo> observations) {
        observations.forEach((grpId, card) -> {
            if (grpId != null && grpId > 0 && card != null) knownCards.put(grpId, card);
        });
    }

    public synchronized Map<Long, GameObjectState> observedCardSnapshot() {
        Map<Long, GameObjectState> snapshot = new LinkedHashMap<>();
        observedCardsByGrpId.forEach((grpId, card) -> snapshot.put(grpId, card.copy()));
        return Map.copyOf(snapshot);
    }

    public synchronized void observeArenaCards(Map<Long, GameObjectState> observations) {
        observations.forEach((grpId, card) -> {
            if (grpId != null && grpId > 0 && card != null) {
                observedCardsByGrpId.put(grpId, card.copy());
            }
        });
    }
}
