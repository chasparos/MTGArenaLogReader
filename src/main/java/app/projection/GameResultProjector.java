package app.projection;

import app.model.event.GameEvent;
import app.model.game.GameResult;
import app.model.game.GameState;
import app.model.game.ZoneInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static app.projection.ArenaJson.arrayAt;
import static app.projection.ArenaJson.intAt;
import static app.projection.ArenaJson.stringAt;

/** Reconstructs the terminal game result from Arena's completion observation. */
final class GameResultProjector {
    record Projection(String text, GameResult result) {}

    private final GameState state;
    private final IntFunction<String> playerName;

    GameResultProjector(GameState state, IntFunction<String> playerName) {
        this.state = state;
        this.playerName = playerName;
    }

    Projection project(JsonObject gameInfo, JsonArray players, List<GameEvent> preceding) {
        GameResult result = new GameResult();
        int winningTeam = -1;
        String explicitReason = "";
        for (JsonElement element : arrayAt(gameInfo, "results")) {
            if (!element.isJsonObject()) continue;
            JsonObject observation = element.getAsJsonObject();
            if ("ResultType_Draw".equals(stringAt(observation, "result"))) {
                result.setReason(GameResult.Reason.DRAW);
                result.setConfidence(GameResult.Confidence.EXPLICIT);
            }
            int team = intAt(observation, "winningTeamId", -1);
            if (team >= 0) winningTeam = team;
            String reason = stringAt(observation, "reason");
            if (!reason.isBlank()) explicitReason = reason;
        }

        Map<Integer, Integer> seatTeams = new LinkedHashMap<>();
        for (JsonElement element : players) {
            if (!element.isJsonObject()) continue;
            JsonObject player = element.getAsJsonObject();
            int seat = intAt(player, "systemSeatNumber", -1);
            int team = intAt(player, "teamId", -1);
            if (seat >= 0) seatTeams.put(seat, team);
        }
        for (Map.Entry<Integer, Integer> entry : seatTeams.entrySet()) {
            if (entry.getValue() == winningTeam) {
                result.setWinnerSeatId(entry.getKey());
                result.setWinnerName(playerName.apply(entry.getKey()));
            } else if (winningTeam >= 0) {
                result.setLoserSeatId(entry.getKey());
                result.setLoserName(playerName.apply(entry.getKey()));
            }
        }

        inferReason(result, explicitReason);
        preceding.stream()
                .filter(event -> !event.getCards().isEmpty())
                .reduce((first, second) -> second)
                .ifPresent(event -> result.setFinishingCard(event.getCards().get(0).getName()));
        return new Projection(description(result), result);
    }

    private void inferReason(GameResult result, String explicitReason) {
        if (result.getReason() == GameResult.Reason.DRAW) return;
        Integer loser = result.getLoserSeatId();
        int poison = loser == null ? 0 : state.getPoisonCounters().getOrDefault(loser, 0);
        Integer life = loser == null ? null : state.getLifeTotals().get(loser);
        Integer library = loser == null ? null : librarySize(loser);
        if (poison >= 10) {
            result.setReason(GameResult.Reason.POISON);
            result.setConfidence(GameResult.Confidence.CORRELATED);
        } else if (life != null && life <= 0) {
            result.setReason(GameResult.Reason.DAMAGE);
            result.setConfidence(GameResult.Confidence.CORRELATED);
        } else if (library != null && library == 0) {
            result.setReason(GameResult.Reason.EMPTY_LIBRARY);
            result.setConfidence(GameResult.Confidence.INFERRED);
        } else if (explicitReason.toLowerCase().contains("concede")) {
            result.setReason(GameResult.Reason.CONCEDE);
            result.setConfidence(GameResult.Confidence.EXPLICIT);
        } else {
            result.setReason(GameResult.Reason.OTHER);
            result.setConfidence(GameResult.Confidence.INFERRED);
        }
    }

    private Integer librarySize(int seat) {
        return state.getZones().values().stream()
                .filter(zone -> zone.getOwnerSeatId() != null && zone.getOwnerSeatId() == seat)
                .filter(zone -> "Library".equals(zone.displayName()))
                .map(ZoneInfo::getObjectCount)
                .filter(count -> count >= 0)
                .findFirst().orElse(null);
    }

    private String description(GameResult result) {
        String winner = result.getWinnerName() == null ? "Game" : result.getWinnerName();
        String text = switch (result.getReason()) {
            case DAMAGE -> winner + " wins by damage";
            case POISON -> winner + " wins by poison";
            case EMPTY_LIBRARY -> winner + " wins because the opponent drew from an empty library";
            case CONCEDE -> winner + " wins by concession";
            case EFFECT -> winner + " wins by a card effect";
            case OTHER -> winner + " wins (reason not identified)";
            case DRAW -> "Game ends in a draw";
            case UNKNOWN -> winner + " wins";
        };
        if (result.getFinishingCard() != null
                && (result.getReason() == GameResult.Reason.EFFECT
                || result.getReason() == GameResult.Reason.UNKNOWN)) {
            text += " via " + result.getFinishingCard();
        }
        return text;
    }
}
