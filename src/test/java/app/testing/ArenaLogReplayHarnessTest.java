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


    @Test
    void seedsPlayerIdentityIntoLaterGamesWithoutAnotherRoomRecord() {
        ArenaLogReplayHarness.ReplayResult result = new ArenaLogReplayHarness().replayLines(List.of(
                roomWithPlayers("match-a", "Alice", "Bob"),
                game(2),
                dieRoll(1, 17)
        ));

        assertEquals(
                List.of("Alice rolled 17"),
                result.requireGame("match-a", 2).snapshot().stream()
                        .map(event -> event.getText())
                        .toList());
    }

    private String room(String matchId) {
        return """
                {"matchGameRoomStateChangedEvent":{"gameRoomInfo":{"gameRoomConfig":{
                  "matchId":"%s"
                }}}}
                """.formatted(matchId);
    }


    private String roomWithPlayers(String matchId, String first, String second) {
        return """
                {"matchGameRoomStateChangedEvent":{"gameRoomInfo":{"gameRoomConfig":{
                  "matchId":"%s",
                  "reservedPlayers":[
                    {"systemSeatId":1,"playerName":"%s"},
                    {"systemSeatId":2,"playerName":"%s"}
                  ]
                }}}}
                """.formatted(matchId, first, second);
    }

    private String dieRoll(int seat, int value) {
        return """
                {"greToClientEvent":{"greToClientMessages":[{
                  "type":"GREMessageType_DieRollResultsResp",
                  "dieRollResultsResp":{"playerDieRolls":[
                    {"systemSeatId":%d,"rollValue":%d}
                  ]}
                }]}}
                """.formatted(seat, value);
    }

    private String game(int gameNumber) {
        return """
                {"greToClientEvent":{"greToClientMessages":[{"gameStateMessage":{
                  "gameInfo":{"gameNumber":%d}
                }}]}}
                """.formatted(gameNumber);
    }
}
