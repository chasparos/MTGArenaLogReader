package app.projection;

import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.game.GameResult;
import app.model.match.MatchResult;
import app.model.match.MatchScore;
import app.model.match.MatchState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects match-lifetime progression from already reconstructed game events.
 *
 * <p>GameEventProjector remains authoritative for game outcomes. This collaborator
 * records each outcome once and appends match score and match result events without
 * inspecting raw Arena payloads.</p>
 */
public final class MatchProjector {
    private final MatchState state;
    private final Set<Integer> startedGames = new HashSet<>();

    public MatchProjector(MatchState state) {
        this.state = state;
    }

    public synchronized List<GameEvent> project(int gameNumber, List<GameEvent> gameEvents) {
        List<GameEvent> result = new ArrayList<>();
        boolean gameStartEmitted = startedGames.contains(gameNumber);

        for (GameEvent event : gameEvents) {
            result.add(event);

            if (event.getType() == GameEventType.MATCH_STARTED) {
                state.markMatchStarted();
                if (!gameStartEmitted) {
                    result.add(gameStartedEvent(gameNumber, event));
                    startedGames.add(gameNumber);
                    gameStartEmitted = true;
                }
            }

            if (event.getGameResult() != null
                    && state.recordGameResult(gameNumber, event.getGameResult())) {
                MatchScore score = state.score();
                result.add(matchScoreEvent(score, event));

                MatchResult matchResult = inferredMatchResult(score);
                if (matchResult != null && state.completeMatch(matchResult)) {
                    result.add(matchResultEvent(matchResult, event));
                }
            }
        }

        if (!gameStartEmitted) {
            GameEvent anchor = gameEvents.isEmpty() ? null : gameEvents.get(0);
            result.add(0, gameStartedEvent(gameNumber, anchor));
            startedGames.add(gameNumber);
        }
        return result;
    }

    private MatchResult inferredMatchResult(MatchScore score) {
        Integer winnerSeat = null;
        if (score.seatOneWins() >= 2) winnerSeat = 1;
        if (score.seatTwoWins() >= 2) winnerSeat = 2;
        if (winnerSeat == null) return null;

        return new MatchResult(
                winnerSeat,
                playerName(winnerSeat),
                score,
                MatchResult.Confidence.INFERRED);
    }

    private GameEvent gameStartedEvent(int gameNumber, GameEvent anchor) {
        GameEvent event = derivedEvent(anchor, "Game " + gameNumber + " started");
        event.setType(GameEventType.GAME_STARTED);
        return event;
    }

    private GameEvent matchScoreEvent(MatchScore score, GameEvent anchor) {
        String text = "Match score: "
                + playerName(1) + " " + score.seatOneWins()
                + " — " + score.seatTwoWins() + " " + playerName(2);
        if (score.draws() > 0) text += " (" + score.draws() + " draw" + (score.draws() == 1 ? "" : "s") + ")";

        GameEvent event = derivedEvent(anchor, text);
        event.setType(GameEventType.MATCH_SCORE);
        event.setMatchScore(score);
        return event;
    }

    private GameEvent matchResultEvent(MatchResult matchResult, GameEvent anchor) {
        MatchScore score = matchResult.finalScore();
        GameEvent event = derivedEvent(anchor,
                matchResult.winnerName() + " wins the match "
                        + score.winsForSeat(matchResult.winnerSeatId())
                        + "–" + score.winsForSeat(matchResult.winnerSeatId() == 1 ? 2 : 1));
        event.setType(GameEventType.MATCH_RESULT);
        event.setMatchScore(score);
        event.setMatchResult(matchResult);
        return event;
    }

    private GameEvent derivedEvent(GameEvent anchor, String text) {
        GameEvent event = new GameEvent();
        if (anchor != null) {
            event.setSequence(anchor.getSequence());
            event.setTimestamp(anchor.getTimestamp());
            event.setTurnNumber(anchor.getTurnNumber());
            event.setActivePlayerSeat(anchor.getActivePlayerSeat());
            event.setActivePlayerName(anchor.getActivePlayerName());
            event.setPhase(anchor.getPhase());
            event.setStep(anchor.getStep());
        }
        event.setText(text);
        return event;
    }

    private String playerName(int seatId) {
        Map<Integer, String> players = state.playerSnapshot();
        return players.getOrDefault(seatId, "Seat " + seatId);
    }
}
