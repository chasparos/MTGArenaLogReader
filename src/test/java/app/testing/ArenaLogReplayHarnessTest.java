package app.testing;

import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke-tests the deterministic end-to-end replay harness before real fixtures are added.
 */
final class ArenaLogReplayHarnessTest {
    @Test
    void createsIndependentModelsForGamesAcrossMatches() {
        ArenaLogReplayHarness.ReplayResult result = new ArenaLogReplayHarness().replayLines(List.of(
                room("match-a"),
                game(1),
                game(2),
                room("match-b"),
                game(1)
        ));

        assertEquals(3, result.games().size());

        GameModel secondGame = result.requireGame("match-a", 2);
        assertEquals("match-a", secondGame.getMatchId());
        assertEquals(2, secondGame.getGameNumber());
        assertEquals(1, secondGame.rawRecordSnapshot().size());

        GameModel newMatch = result.requireGame("match-b", 1);
        assertEquals(2, newMatch.rawRecordSnapshot().size());
    }

    private String room(String matchId) {
        return """
                {"matchGameRoomStateChangedEvent":{"gameRoomInfo":{"gameRoomConfig":{
                  "matchId":"%s"
                }}}}
                """.formatted(matchId);
    }

    private String game(int gameNumber) {
        return """
                {"greToClientEvent":{"greToClientMessages":[{"gameStateMessage":{
                  "gameInfo":{"gameNumber":%d}
                }}]}}
                """.formatted(gameNumber);
    }
}
