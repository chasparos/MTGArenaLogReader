package app.testing;

import app.model.game.GameKey;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the representative two-match fixture travels through the complete
 * non-UI replay pipeline without leaking state between matches or games.
 *
 * <p>This is the broad regression boundary for framing, decoding, routing, canonical
 * projection, and per-game model creation. Focused semantic assertions should be added
 * in separate tests as reconstruction behavior is specified.</p>
 */
final class MultigameLogReplayTest {
    private static final String FIRST_MATCH = "1931e9b0-e0ea-4361-9312-881e159dcd88";
    private static final String SECOND_MATCH = "8f8dc28c-31f9-44c9-a990-6f201ba96e6e";

    @Test
    void reconstructsTwoIndependentThreeGameMatches() throws Exception {
        ArenaLogReplayHarness.ReplayResult result =
                new ArenaLogReplayHarness().replay(fixture("logs/multigame.log"));

        assertEquals(6, result.games().size());
        assertEquals(Set.of(FIRST_MATCH, SECOND_MATCH), matchIds(result.games()));
        assertEquals(List.of(1, 2, 3), gameNumbers(result.games(), FIRST_MATCH));
        assertEquals(List.of(1, 2, 3), gameNumbers(result.games(), SECOND_MATCH));

        for (String matchId : Set.of(FIRST_MATCH, SECOND_MATCH)) {
            for (int gameNumber = 1; gameNumber <= 3; gameNumber++) {
                final int finalGameNumber = gameNumber;
                GameModel game = result.requireGame(matchId, gameNumber);
                assertEquals(matchId, game.getMatchId());
                assertEquals(gameNumber, game.getGameNumber());
                assertTrue(game.rawRecordSnapshot().size() > 10,
                        () -> "Expected routed records for " + matchId + " game " + finalGameNumber);
                assertTrue(game.snapshot().size() > 5,
                        () -> "Expected projected events for " + matchId + " game " + finalGameNumber);
            }
        }

        assertDistinctModels(result, FIRST_MATCH);
        assertDistinctModels(result, SECOND_MATCH);
        assertNotSame(result.requireGame(FIRST_MATCH, 3), result.requireGame(SECOND_MATCH, 1));
    }

    @Test
    void replayingWithTheSameHarnessStartsFromCleanState() throws Exception {
        ArenaLogReplayHarness harness = new ArenaLogReplayHarness();
        Path fixture = fixture("logs/multigame.log");

        ArenaLogReplayHarness.ReplayResult first = harness.replay(fixture);
        ArenaLogReplayHarness.ReplayResult second = harness.replay(fixture);

        assertEquals(first.games().keySet(), second.games().keySet());
        first.games().forEach((key, game) -> {
            GameModel replayed = second.games().get(key);
            assertEquals(game.rawRecordSnapshot().size(), replayed.rawRecordSnapshot().size());
            assertEquals(
                    game.snapshot().stream().map(event -> event.getText()).toList(),
                    replayed.snapshot().stream().map(event -> event.getText()).toList(),
                    () -> "Projected event text changed between replays for " + key);
        });
    }

    private void assertDistinctModels(ArenaLogReplayHarness.ReplayResult result, String matchId) {
        GameModel first = result.requireGame(matchId, 1);
        GameModel second = result.requireGame(matchId, 2);
        GameModel third = result.requireGame(matchId, 3);
        assertNotSame(first, second);
        assertNotSame(first, third);
        assertNotSame(second, third);
    }

    private Set<String> matchIds(Map<GameKey, GameModel> games) {
        return games.keySet().stream().map(GameKey::getMatchId).collect(Collectors.toSet());
    }

    private List<Integer> gameNumbers(Map<GameKey, GameModel> games, String matchId) {
        return games.keySet().stream()
                .filter(key -> matchId.equals(key.getMatchId()))
                .map(GameKey::getGameNumber)
                .sorted()
                .toList();
    }

    private Path fixture(String resource) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(resource).toURI());
    }
}
