package app.projection;

import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.game.GameResult;
import app.model.match.MatchResult;
import app.model.match.MatchScore;
import app.model.match.MatchState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MatchProjectorTest {
    @Test
    void emitsGameResultThenScoreThenMatchResult() {
        MatchState state = stateWithPlayers();
        MatchProjector projector = new MatchProjector(state);

        projector.project(1, List.of(gameResult(1, "Alice")));
        List<GameEvent> second = projector.project(2, List.of(gameResult(1, "Alice")));

        assertEquals(
                List.of(
                        GameEventType.GAME_STARTED,
                        GameEventType.GAME_RESULT,
                        GameEventType.MATCH_SCORE,
                        GameEventType.MATCH_RESULT),
                second.stream().map(GameEvent::getType).toList());
        assertEquals("Alice wins the match 2–0", second.get(3).getText());
        assertEquals(MatchResult.Confidence.INFERRED,
                second.get(3).getMatchResult().confidence());
    }

    @Test
    void reconstructsThreeGameScoreProgression() {
        MatchState state = stateWithPlayers();
        MatchProjector projector = new MatchProjector(state);

        List<GameEvent> first = projector.project(1, List.of(gameResult(1, "Alice")));
        List<GameEvent> second = projector.project(2, List.of(gameResult(2, "Bob")));
        List<GameEvent> third = projector.project(3, List.of(gameResult(1, "Alice")));

        assertEquals(new MatchScore(1, 0, 0), scoreEvent(first).getMatchScore());
        assertEquals(new MatchScore(1, 1, 0), scoreEvent(second).getMatchScore());
        assertEquals(new MatchScore(2, 1, 0), scoreEvent(third).getMatchScore());
        assertEquals("Alice wins the match 2–1", third.get(3).getText());
    }

    @Test
    void recordsDrawWithoutInventingWinner() {
        MatchState state = stateWithPlayers();
        MatchProjector projector = new MatchProjector(state);

        List<GameEvent> events = projector.project(1, List.of(drawResult()));

        assertEquals(new MatchScore(0, 0, 1), scoreEvent(events).getMatchScore());
        assertNull(state.matchResult());
    }

    @Test
    void emitsEachGameStartOnlyOnce() {
        MatchProjector projector = new MatchProjector(stateWithPlayers());

        List<GameEvent> first = projector.project(1, List.of(gameplay("Alice rolled 17")));
        List<GameEvent> second = projector.project(1, List.of(gameplay("Bob rolled 12")));

        assertEquals(GameEventType.GAME_STARTED, first.get(0).getType());
        assertEquals(2, first.size());
        assertEquals(1, second.size());
        assertEquals(GameEventType.GAMEPLAY, second.get(0).getType());
    }

    private MatchState stateWithPlayers() {
        MatchState state = new MatchState("match-a");
        state.observePlayers(Map.of(1, "Alice", 2, "Bob"));
        return state;
    }

    private GameEvent gameResult(int winnerSeat, String winnerName) {
        GameResult result = new GameResult();
        result.setWinnerSeatId(winnerSeat);
        result.setWinnerName(winnerName);
        result.setLoserSeatId(winnerSeat == 1 ? 2 : 1);
        result.setReason(GameResult.Reason.OTHER);
        result.setConfidence(GameResult.Confidence.INFERRED);

        GameEvent event = gameplay(winnerName + " wins");
        event.setType(GameEventType.GAME_RESULT);
        event.setGameResult(result);
        return event;
    }

    private GameEvent drawResult() {
        GameResult result = new GameResult();
        result.setReason(GameResult.Reason.DRAW);
        result.setConfidence(GameResult.Confidence.EXPLICIT);

        GameEvent event = gameplay("Game ends in a draw");
        event.setType(GameEventType.GAME_RESULT);
        event.setGameResult(result);
        return event;
    }

    private GameEvent gameplay(String text) {
        GameEvent event = new GameEvent();
        event.setSequence(1);
        event.setTimestamp(Instant.EPOCH);
        event.setText(text);
        return event;
    }

    private GameEvent scoreEvent(List<GameEvent> events) {
        return events.stream()
                .filter(event -> event.getType() == GameEventType.MATCH_SCORE)
                .findFirst()
                .orElseThrow();
    }
}
