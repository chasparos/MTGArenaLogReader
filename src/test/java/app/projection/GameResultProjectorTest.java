package app.projection;

import app.model.game.GameResult;
import app.model.game.GameState;
import app.model.game.ZoneInfo;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameResultProjectorTest {
    @Test
    void correlatesLethalLifeTotalWithDamage() {
        GameState state = state();
        state.getLifeTotals().put(2, 0);

        GameResultProjector.Projection projection = projector(state).project(
                gameInfo("ResultType_WinLoss", ""),
                players(),
                List.of());

        assertEquals(GameResult.Reason.DAMAGE, projection.result().getReason());
        assertEquals(GameResult.Confidence.CORRELATED, projection.result().getConfidence());
        assertEquals("Alice wins by damage", projection.text());
    }

    @Test
    void recognizesExplicitDrawWithoutInventingWinner() {
        GameResultProjector.Projection projection = projector(state()).project(
                gameInfo("ResultType_Draw", ""),
                players(),
                List.of());

        assertEquals(GameResult.Reason.DRAW, projection.result().getReason());
        assertEquals(GameResult.Confidence.EXPLICIT, projection.result().getConfidence());
        assertEquals("Game ends in a draw", projection.text());
    }

    @Test
    void infersEmptyLibraryFromCanonicalZoneState() {
        GameState state = state();
        ZoneInfo library = new ZoneInfo();
        library.setOwnerSeatId(2);
        library.setType("ZoneType_Library");
        library.setObjectCount(0);
        state.getZones().put(12, library);

        GameResultProjector.Projection projection = projector(state).project(
                gameInfo("ResultType_WinLoss", ""),
                players(),
                List.of());

        assertEquals(GameResult.Reason.EMPTY_LIBRARY, projection.result().getReason());
        assertEquals(GameResult.Confidence.INFERRED, projection.result().getConfidence());
    }

    private static GameState state() {
        GameState state = new GameState();
        state.getPlayers().put(1, "Alice");
        state.getPlayers().put(2, "Bob");
        return state;
    }

    private static GameResultProjector projector(GameState state) {
        return new GameResultProjector(state,
                seat -> state.getPlayers().getOrDefault(seat, "Player " + seat));
    }

    private static com.google.gson.JsonObject gameInfo(String result, String reason) {
        return JsonParser.parseString("""
                {"results":[{"result":"%s","winningTeamId":1,"reason":"%s"}]}
                """.formatted(result, reason)).getAsJsonObject();
    }

    private static com.google.gson.JsonArray players() {
        return JsonParser.parseString("""
                [{"systemSeatNumber":1,"teamId":1},{"systemSeatNumber":2,"teamId":2}]
                """).getAsJsonArray();
    }
}
