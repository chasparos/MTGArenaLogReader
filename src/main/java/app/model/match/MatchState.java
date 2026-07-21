package app.model.match;

import app.model.card.CardInfo;
import app.model.game.GameObjectState;

import app.model.game.GameResult;

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
    private final Map<Integer, GameResult> completedGames = new LinkedHashMap<>();
    private boolean matchStarted;
    private MatchResult matchResult;

    public MatchState(String matchId) {
        this.matchId = matchId;
    }

    public String getMatchId() {
        return matchId;
    }


    public synchronized boolean markMatchStarted() {
        if (matchStarted) return false;
        matchStarted = true;
        return true;
    }

    public synchronized boolean recordGameResult(int gameNumber, GameResult result) {
        if (gameNumber <= 0 || result == null || completedGames.containsKey(gameNumber)) return false;
        completedGames.put(gameNumber, result);
        return true;
    }

    public synchronized MatchScore score() {
        int seatOneWins = 0;
        int seatTwoWins = 0;
        int draws = 0;
        for (GameResult result : completedGames.values()) {
            if (result.getReason() == GameResult.Reason.DRAW) {
                draws++;
            } else if (Integer.valueOf(1).equals(result.getWinnerSeatId())) {
                seatOneWins++;
            } else if (Integer.valueOf(2).equals(result.getWinnerSeatId())) {
                seatTwoWins++;
            }
        }
        return new MatchScore(seatOneWins, seatTwoWins, draws);
    }

    public synchronized MatchResult matchResult() {
        return matchResult;
    }

    public synchronized boolean completeMatch(MatchResult result) {
        if (matchResult != null || result == null) return false;
        matchResult = result;
        return true;
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
