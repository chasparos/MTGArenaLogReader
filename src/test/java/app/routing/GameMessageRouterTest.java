package app.routing;


import app.routing.GameMessageRouter;
import app.model.game.GameKey;
import app.model.log.LogMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that match and game context remain isolated across ordered Arena records.
 */
final class GameMessageRouterTest {
    @Test
    void routesMultipleGamesAndResetsGameNumberForNewMatch() {
        GameMessageRouter router = new GameMessageRouter();

        assertEquals(new GameKey("match-a", 1), router.route(message(room("match-a"))).orElseThrow());
        assertEquals(new GameKey("match-a", 2), router.route(message(game(2))).orElseThrow());
        assertEquals(new GameKey("match-b", 1), router.route(message(room("match-b"))).orElseThrow());
    }

    private LogMessage message(String raw) {
        LogMessage message = new LogMessage();
        message.setRawText(raw);
        return message;
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
